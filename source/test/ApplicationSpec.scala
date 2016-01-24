import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.Play.current

import play.api.test._
import play.api.test.Helpers._

import play.modules.reactivemongo.{
MongoController, ReactiveMongoApi, ReactiveMongoComponents
}
import play.api.i18n.MessagesApi

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  lazy val reactiveMongoApi = current.injector.instanceOf[ReactiveMongoApi]
  lazy val messagesApi = current.injector.instanceOf[MessagesApi]
  val applicationController = new controllers.Application(reactiveMongoApi, messagesApi)

  "Application" should {

    "redirect to the product list on /" in {

      val result = applicationController.index(FakeRequest())

      status(result) must equalTo(SEE_OTHER)
      redirectLocation(result) must beSome.which(_ == "/list")

    }
  }
}

