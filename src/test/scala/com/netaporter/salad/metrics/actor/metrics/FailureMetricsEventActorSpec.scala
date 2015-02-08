package com.netaporter.salad.metrics.actor.metrics

import com.netaporter.salad.metrics.messages.MetricEventMessage.IncCounterEvent
import com.netaporter.salad.metrics.spray.metrics.MetricsDirectiveFactory
import com.netaporter.salad.metrics.util.ActorSys
import org.scalatest.{ ParallelTestExecution, fixture }
import spray.http.StatusCodes
import spray.routing.{ HttpService, Route, ValidationRejection }
import spray.testkit.ScalatestRouteTest

/**
 * Created by d.tootell@london.net-a-porter.com on 03/02/2014.
 */
class FailureMetricsEventActorSpec extends fixture.WordSpec with ScalatestRouteTest with fixture.UnitFixture
    with ParallelTestExecution with HttpService {

  def actorRefFactory = system

  "Metrics Event Actor" should {

    "should capture rejections" in new Context {
      val validationRejection = ValidationRejection("Restricted!")
      val route = testRoute {
        reject(validationRejection)
      }

      Get() ~> route ~> check {
        assert(rejection == validationRejection)
      }
      expectMsg(IncCounterEvent("methodName.GET.rejections"))
    }

    "should capture failures" in new Context {
      val route = testRoute {
        complete(StatusCodes.NotFound)
      }

      Get() ~> route ~> check {
        assert(status == StatusCodes.NotFound)
      }
      expectMsg(IncCounterEvent("methodName.GET.failures"))
    }

    "should capture exceptions" in new Context {
      val route = testRoute {
        failWith(new RuntimeException("test exception"))
      }

      Get() ~> route ~> check {
        assert(status == StatusCodes.InternalServerError)
      }
      expectMsg(IncCounterEvent("methodName.GET.exceptions"))
    }

    "should capture thrown exceptions" in new Context {
      val route = testRoute {
        complete(throw new RuntimeException("test exception"))
      }

      Get() ~> route ~> check {
        assert(status == StatusCodes.InternalServerError)
      }
      expectMsg(IncCounterEvent("methodName.GET.exceptions"))
    }
  }

  class Context extends ActorSys {
    def testRoute(body: => Route) = {
      val requestCounter = MetricsDirectiveFactory(self).counterWithMethod("methodName").all.count
      requestCounter {
        get {
          pathSingleSlash {
            body
          }
        }
      }
    }
  }

}
