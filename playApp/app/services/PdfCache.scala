package services

import java.util.concurrent.atomic.AtomicInteger
import javax.inject._
import collection.mutable.Map

@Singleton
class PdfCache {  
  private val cache = Map[Int, String]()
  def put(id: Int, path: String){
    cache += (id -> path)
  }
  def get(id: Int): String = {
    cache(id)
  }

}
