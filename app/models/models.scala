package models

import reactivemongo.bson.BSONObjectID

case class Category(_id:  BSONObjectID, name: String)
case class Product(_id: BSONObjectID, name: String, description: String, categoryId: String)

object JsonFormats {
  import play.api.libs.json.Json
  import play.api.data._
  import play.api.data.Forms._
  import play.modules.reactivemongo.json.BSONFormats._

  // Generates Writes and Reads for Feed and User thanks to Json Macros
  implicit val categoryFormat = Json.format[Category]
  implicit val productFormat = Json.format[Product]
}