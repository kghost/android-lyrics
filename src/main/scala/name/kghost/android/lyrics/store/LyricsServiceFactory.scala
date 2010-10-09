package name.kghost.android.lyrics.store

import scala.collection.immutable.HashMap
import name.kghost.android.lyrics.utils.With
import android.content.Context

class LyricsServiceFactory(context: Context) {
  val local = new LocalStorageService(context)
  def get(name: String): LyricsService = if (name == local.name)
    local
  else
    LyricsServiceFactory.hash(name)
}

object LyricsServiceFactory {
  private final val list = new QianQianService :: new LyricistService :: Nil
  private val hash: Map[String, LyricsService] = HashMap() ++ (for (i <- list) yield { i.name }).zip(list)
}
