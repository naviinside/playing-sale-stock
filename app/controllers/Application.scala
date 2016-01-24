package controllers

import java.util.concurrent.TimeoutException

import javax.inject.Inject

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{ Action, Controller }
import play.api.data.Form
import play.api.data.Forms.{ date, ignored, mapping, nonEmptyText, optional, text }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json, Json.toJsFieldJsValueWrapper

import play.modules.reactivemongo.{
MongoController, ReactiveMongoApi, ReactiveMongoComponents
}
import play.modules.reactivemongo.json._, collection.JSONCollection

import reactivemongo.bson.BSONObjectID

import models.{ Product, JsonFormats, Category }, JsonFormats.categoryFormat, JsonFormats.productFormat
import views.html

class Application @Inject()(
    val reactiveMongoApi: ReactiveMongoApi,
    val messagesApi: MessagesApi)
    extends Controller with MongoController with ReactiveMongoComponents {

  implicit val timeout = 10.seconds

  /*
  * Get a JSONCollection (a Collection implementation that is designed to work
  * with JsObject, Reads and Writes.)
  * Note that the `collection` is not a `val`, but a `def`. We do _not_ store
  * the collection reference to avoid potential problems in development with
  * Play hot-reloading.
  */
  def categoryCollection: JSONCollection = db.collection[JSONCollection]("categories")
  def productCollection: JSONCollection = db.collection[JSONCollection]("products")

  /**
    * Describe the category form (used in both edit and create screens).
    */
  val categoryForm = Form(
    mapping(
      "id" -> ignored(BSONObjectID.generate: BSONObjectID),
      "name" -> nonEmptyText
    )(Category.apply)(Category.unapply)
  )

  /**
    * Describe the product form (used in both edit and create screens).
    */
  val productForm = Form(
    mapping(
      "id" -> ignored(BSONObjectID.generate: BSONObjectID),
      "name" -> nonEmptyText,
      "description" -> nonEmptyText,
      "categoryId" -> text
    )(Product.apply)(Product.unapply)
  )

  /**
    * This result directly redirect to the application home.
    */
  val Home = Redirect(routes.Application.list)

  /**
    * Handle default path requests, redirect to products list
    */
  def index = Action { Home }

  /**
    * Display the paginated list of products and categories.
    *
    */
  def list = Action.async { implicit request =>
    val futureCategory =  categoryCollection.genericQueryBuilder.cursor[Category]().collect[List]()
    val futureProduct =  productCollection.genericQueryBuilder.cursor[Product]().collect[List]()

    val combinedFuture =
      for {
        f1 <- futureCategory
        f2 <- futureProduct
      } yield (f1, f2)

    combinedFuture.map({case (categories, products) =>
      implicit val msg = messagesApi.preferred(request)
      Ok(html.list(categories, products))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in list process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Display the 'new category form'.
    */
  def addCategory = Action { request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(html.addCategoryForm(categoryForm))
  }

  /**
    * Handle the 'new category form' submission.
    */
  def saveCategory = Action.async { implicit request =>
    categoryForm.bindFromRequest.fold(
      { formWithErrors =>
        implicit val msg = messagesApi.preferred(request)
        Future.successful(BadRequest(html.addCategoryForm(formWithErrors)))
      },
      category => {
        val futureUpdate = categoryCollection.insert(category.copy(_id = BSONObjectID.generate))
        futureUpdate.map { result =>
          Home.flashing("success" -> s"Category ${category.name} has been created")
        }.recover {
          case t: TimeoutException =>
            Logger.error("Problem found in category update process")
            InternalServerError(t.getMessage)
        }
      })
  }

  /**
    * Display the 'edit form' of a existing Category.
    */
  def editCategory(id: String) = Action.async { request =>
    val futureCategory = categoryCollection.find(Json.obj("_id" -> Json.obj("$oid" -> id))).cursor[Category]().collect[List]()

    futureCategory.map { categories: List[Category] =>
      implicit val msg = messagesApi.preferred(request)

      Ok(html.editCategoryForm(id, categoryForm.fill(categories.head)))
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in category edit process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Handle the 'edit form' submission
    */
  def updateCategory(id: String) = Action.async { implicit request =>
    categoryForm.bindFromRequest.fold(
      { formWithErrors =>
        implicit val msg = messagesApi.preferred(request)
        Future.successful(BadRequest(html.editCategoryForm(id, formWithErrors)))
      },
      category => {
        val futureUpdate = categoryCollection.update(Json.obj("_id" -> Json.obj("$oid" -> id)), category.copy(_id = BSONObjectID(id)))
        futureUpdate.map { result =>
          Home.flashing("success" -> s"Category ${category.name} has been updated")
        }.recover {
          case t: TimeoutException =>
            Logger.error("Problem found in category update process")
            InternalServerError(t.getMessage)
        }
      })
  }

  /**
    * Handle category deletion.
    */
  def deleteCategory(id: String) = Action.async {
    val futureInt = categoryCollection.remove(Json.obj("_id" -> Json.obj("$oid" -> id)), firstMatchOnly = true)
    futureInt.map(i => Home.flashing("success" -> "Category has been deleted")).recover {
      case t: TimeoutException =>
        Logger.error("Problem deleting category")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Display the 'new product form'.
    */
  def addProduct = Action.async { implicit request =>
    val futureCategory =  categoryCollection.genericQueryBuilder.cursor[Category]().collect[List]()

    futureCategory.map ({categories =>
        implicit val msg = messagesApi.preferred(request)
        Ok(html.addProductForm(productForm, categories))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in list process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Handle the 'new product form' submission.
    */
  def saveProduct = Action.async { implicit request =>
    val futureCategory =  categoryCollection.genericQueryBuilder.cursor[Category]().collect[List]()

    productForm.bindFromRequest.fold(
      { formWithErrors =>
        implicit val msg = messagesApi.preferred(request)
        val categories = Seq.empty[Category]
        Future.successful(BadRequest(html.addProductForm(formWithErrors, categories)))
      },
      product => {
        val futureUpdate = productCollection.insert(product.copy(_id = BSONObjectID.generate))

        futureUpdate.map { result =>
          Home.flashing("success" -> s"Product ${product.name} has been created")
        }.recover {
          case t: TimeoutException =>
            Logger.error("Problem found in product update process")
            InternalServerError(t.getMessage)
        }
      })
  }

  /**
    * Display the 'edit form' of a existing Product.
    */
  def editProduct(id: String) = Action.async { request =>
    val futureCategory =  categoryCollection.genericQueryBuilder.cursor[Category]().collect[List]()
    val futureProduct = productCollection.find(Json.obj("_id" -> Json.obj("$oid" -> id))).cursor[Product]().collect[List]()

    val combinedFuture =
      for {
        f1 <- futureCategory
        f2 <- futureProduct
      } yield (f1, f2)

    combinedFuture.map { case (categories, products)  =>
      implicit val msg = messagesApi.preferred(request)

      Ok(html.editProductForm(id, productForm.fill(products.head), categories))
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in product edit process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Handle the 'edit form' submission
    */
  def updateProduct(id: String) = Action.async { implicit request =>
    productForm.bindFromRequest.fold(
      { formWithErrors =>
        implicit val msg = messagesApi.preferred(request)
        val categories = Seq.empty[Category]
        Future.successful(BadRequest(html.editProductForm(id, formWithErrors, categories)))
      },
      product => {
        val futureUpdate = productCollection.update(Json.obj("_id" -> Json.obj("$oid" -> id)), product.copy(_id = BSONObjectID(id)))
        futureUpdate.map { result =>
          Home.flashing("success" -> s"Product ${product.name} has been updated")
        }.recover {
          case t: TimeoutException =>
            Logger.error("Problem found in product update process")
            InternalServerError(t.getMessage)
        }
      })
  }

  /**
    * Handle category deletion.
    */
  def deleteProduct(id: String) = Action.async {
    val futureInt = productCollection.remove(Json.obj("_id" -> Json.obj("$oid" -> id)), firstMatchOnly = true)
    futureInt.map(i => Home.flashing("success" -> "Product has been deleted")).recover {
      case t: TimeoutException =>
        Logger.error("Problem deleting product")
        InternalServerError(t.getMessage)
    }
  }
}
