package name.kghost.android.lyrics.store

case class LyricsServiceError(message: String) extends Exception(message)