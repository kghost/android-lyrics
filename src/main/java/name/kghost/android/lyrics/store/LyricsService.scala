package name.kghost.android.lyrics.store
import scala.collection.immutable.HashMap

abstract class LyricsService {
  def find(info: LyricsSearchInfo): Seq[LyricsResultInfo]
}

object LyricsService {
  def find(info: LyricsSearchInfo): Seq[LyricsResultInfo] = QianQianService.find(info)
}
