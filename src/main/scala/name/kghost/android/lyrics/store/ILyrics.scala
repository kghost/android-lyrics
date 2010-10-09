package name.kghost.android.lyrics.store

abstract class ILyrics {
  def artist: String
  def album: String
  def track: String
  def lyrics: String
}