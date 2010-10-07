package name.kghost.android.lyrics.store

import scala.collection.immutable.HashMap
import name.kghost.android.lyrics.utils.With
import android.content.Context

class LyricsServiceFactory(context: Context) {
  def local: LocalStorageService = create("local").asInstanceOf[LocalStorageService]
  def create(name: String): LyricsService = LyricsServiceFactory.synchronized {
    if (LyricsServiceFactory.hash.contains(name))
      LyricsServiceFactory.hash(name)
    else
      With(name match {
        case "local" => new LocalStorageService(context)
        case "qianqian" => new QianQianService
      }) { s =>
        LyricsServiceFactory.hash += ((name, s))
      }
  }
}

object LyricsServiceFactory {
  var hash = new HashMap[String, LyricsService]
}