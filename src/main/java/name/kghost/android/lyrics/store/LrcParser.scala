package name.kghost.android.lyrics.store

import android.util.Log
import java.io.{ InputStream, InputStreamReader, BufferedReader, IOException }

object LrcParser {
  private val rArtist = """^\s*\[ar:(.*)\]\s*$""".r
  private val rAlbum = """^\s*\[al:(.*)\]\s*$""".r
  private val rTrack = """^\s*\[ti:(.*)\]\s*$""".r
  private val rAuthor = """^\s*\[au:(.*)\]\s*$""".r
  private val rBy = """^\s*\[by:(.*)\]\s*$""".r
  private val rTimeline = """^\[(\d\d+):(\d\d).(\d\d)\](.*)$""".r

  private def parseRecursive(reader: BufferedReader, infoCombiner: (String, String) => String, artist: String, album: String, track: String,
    lines: List[(Int, String)]): (String, String, String, List[(Int, String)]) =
    reader.readLine match {
      case null =>
        (artist, album, track, lines)
      case rTimeline(minites, seconds, milliseconds, line) =>
        parseRecursive(reader, infoCombiner, artist, album, track, ((minites.toInt * 60
          + seconds.toInt) * 1000 + milliseconds.toInt, line) :: lines)
      case rArtist(s) =>
        parseRecursive(reader, infoCombiner, infoCombiner(artist, s), album, track, lines)
      case rAlbum(s) =>
        parseRecursive(reader, infoCombiner, artist, infoCombiner(album, s), track, lines)
      case rTrack(s) =>
        parseRecursive(reader, infoCombiner, artist, album, infoCombiner(track, s), lines)
      case rAuthor(s) =>
        parseRecursive(reader, infoCombiner, artist, album, track, lines)
      case rBy(s) =>
        parseRecursive(reader, infoCombiner, artist, album, track, lines)
      case x => {
        Log.i("LyricsParser", "Discard: " + x);
        parseRecursive(reader, infoCombiner, artist, album, track, lines)
      }
    }

  private def parseStream(input: InputStreamReader, info: LyricsResultInfo, infoCombiner: (String, String) => String): (String, String, String, List[(Int, String)]) =
    parseRecursive(new BufferedReader(input), infoCombiner, info.artist, info.album, info.track, Nil)

  private def composeRecursive(lines: List[(Int, String)], result: List[(Int, Int, String)], last_time: Int): List[(Int, Int, String)] =
    lines match {
      case (time, line) :: rest => composeRecursive(rest, (time, last_time, line) :: result, time)
      case Nil => result
    }

  private def compose(lines: List[(Int, String)]): List[(Int, Int, String)] =
    composeRecursive(lines, Nil, -1)

  private case class LyricsWithTimeline(artist: String, album: String, track: String, timeline: Seq[(Int, Int, String)]) extends ILyricsWithTimeline
  def parse(stream: InputStream, info: LyricsResultInfo, infoCombiner: (String, String) => String): ILyricsWithTimeline =
    parseStream(new InputStreamReader(stream, "UTF-8"), info, infoCombiner) match {
      case (artist, album, track, lines) =>
        LyricsWithTimeline(artist, album, track, compose(lines))
      case null => null
    }
}
