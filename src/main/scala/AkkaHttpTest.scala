import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.misc.Local
import org.mdedetrich.monix.mdc.MonixMDCAdapter
import org.slf4j.MDC

import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}

object AkkaHttpTest extends App with StrictLogging {
  MonixMDCAdapter.initialize()
  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val scheduler: Scheduler            = Scheduler.traced
  implicit val opts: Task.Options              = Task.defaultOptions.enableLocalContextPropagation.disableLocalContextIsolateOnRun

  val addResponseHeader: Directive0 =
    mapInnerRoute(_.andThen(_.flatMap {
      case result @ RouteResult.Complete(response) =>
        Future {
          val l = MDC.get("local")
          if (l != null)
            RouteResult.Complete(response.addHeader(RawHeader("local", l)))
          else
            result
        }
      case result @ RouteResult.Rejected(_) =>
        Future {
          result
        }
    }))

  def onCompleteLocal[T](task: => Task[T]): Directive1[Try[T]] = {
    import akka.http.scaladsl.util.FastFuture._
    Directive { inner => ctx =>
      task.runToFutureOpt.fast.transformWith(t => inner(Tuple1(t))(ctx))
    }
  }

  def routes: Route =
    path("hello" / IntNumber) { id =>
      get {
        addResponseHeader {
          onCompleteLocal(Task {
            Local.isolate {
              MDC.put("local", id.toString)
            }
          }) { _ =>
            complete(StatusCodes.OK)
          }
        }
      }
    }

  val serverBinding = Await.result(Http(actorSystem).bindAndHandle(routes, "localhost", 8080), 1 minutes)

  val http = Http()

  val futures = List.fill(512) {
    val int = Random.nextInt(1000)
    for {
      result <- http.singleRequest(
                 HttpRequest(
                   HttpMethods.GET,
                   Uri(s"http://localhost:8080/hello/$int")
                 ))
      header = result.getHeader("local").asScala
      output = s"input $int: output: ${header.map(_.value()).getOrElse("no header")}"
      _      = result.discardEntityBytes()
      _      = println(output)
    } yield ()
  }

  Await.result(Future.sequence(futures), 1 minutes)
  Await.result(serverBinding.terminate(1 minutes), 1 minutes)

  System.exit(0)
}
