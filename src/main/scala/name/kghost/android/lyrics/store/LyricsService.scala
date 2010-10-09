package name.kghost.android.lyrics.store
import scala.collection.immutable.HashMap

abstract class LyricsService {
  def provider: Int
  def name: String
  def find(info: LyricsSearchInfo): Seq[LyricsResultInfo]
}

