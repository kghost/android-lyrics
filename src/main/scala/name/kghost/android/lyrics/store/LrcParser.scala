package name.kghost.android.lyrics.store

import name.kghost.android.lyrics.utils.UnicodeInputStream
import android.util.Log
import java.io.{ InputStream, InputStreamReader, BufferedReader, IOException, Reader }

object LrcParser {
  private val rArtist = """^\s*\[ar:(.*)\]\s*$""".r
  private val rAlbum = """^\s*\[al:(.*)\]\s*$""".r
  private val rTrack = """^\s*\[ti:(.*)\]\s*$""".r
  private val rAuthor = """^\s*\[au:(.*)\]\s*$""".r
  private val rBy = """^\s*\[by:(.*)\]\s*$""".r
  private val rOffset = """^\s*\[offset:\+?(\-?\d\d*)\]$""".r
  private val rTimeline = """^\[(\d\d+):(\d\d).(\d\d)\](.*)$""".r

  // it is not tail recursive but on most times it do not repeat
  private def parseMultiTimeline(s: String, lines: List[(Int, String)]): (String, List[(Int, String)]) = {
    s match {
      case rTimeline(minites, seconds, milliseconds, line) =>
        parseMultiTimeline(line, lines) match {
          case (s, l) => (s, ((minites.toInt * 60 + seconds.toInt) * 1000 + milliseconds.toInt, s) :: l)
        }
      case x => (x, lines)
    }
  }

  private def parseRecursive(reader: BufferedReader, infoCombiner: (String, String) => String, artist: String,
    album: String, track: String, lines: List[(Int, String)], offset: Int): (String, String, String, List[(Int, String)], Int) =
    reader.readLine match {
      case null => (artist, album, track, lines, offset)
      case rTimeline(minites, seconds, milliseconds, line) =>
        parseRecursive(reader, infoCombiner, artist, album, track, parseMultiTimeline(line, lines) match {
          case (s, l) => ((minites.toInt * 60 + seconds.toInt) * 1000 + milliseconds.toInt, s) :: l
        }, offset)
      case rArtist(s) => parseRecursive(reader, infoCombiner, infoCombiner(artist, s), album, track, lines, offset)
      case rAlbum(s) => parseRecursive(reader, infoCombiner, artist, infoCombiner(album, s), track, lines, offset)
      case rTrack(s) => parseRecursive(reader, infoCombiner, artist, album, infoCombiner(track, s), lines, offset)
      case rAuthor(s) => parseRecursive(reader, infoCombiner, artist, album, track, lines, offset)
      case rBy(s) => parseRecursive(reader, infoCombiner, artist, album, track, lines, offset)
      case rOffset(s) => parseRecursive(reader, infoCombiner, artist, album, track, lines, s.toInt)
      case x => {
        Log.w("LyricsParser", "Skip line: " + x)
        parseRecursive(reader, infoCombiner, artist, album, track, lines, offset)
      }
    }

  private def parseStream(input: Reader, info: LyricsResultInfo, infoCombiner: (String, String) => String): (String, String, String, List[(Int, String)]) =
    parseRecursive(new BufferedReader(input), infoCombiner, info.artist, info.album, info.track, Nil, 0) match {
      case (artist, album, track, lines, offset) =>
        (artist, album, track, lines.map {
          case (s, l) => {
            (s + offset, l)
          }
        })
    }

  private def composeRecursive(lines: List[(Int, String)], result: List[(Int, Int, String)], last_time: Int): List[(Int, Int, String)] =
    lines match {
      case (time, line) :: rest => composeRecursive(rest, (time, last_time, line) :: result, time)
      case Nil => result
    }

  private def compose(lines: List[(Int, String)]): List[(Int, Int, String)] =
    composeRecursive(lines, Nil, -1)

  private case class LyricsWithTimeline(artist: String, album: String, track: String, timeline: Seq[(Int, Int, String)]) extends ILyricsWithTimeline
  def parse(stream: InputStream, info: LyricsResultInfo, infoCombiner: (String, String) => String): ILyricsWithTimeline =
    parseStream(new UnicodeInputStream(stream, "UTF-8"), info, infoCombiner) match {
      case (artist, album, track, lines) =>
        LyricsWithTimeline(artist, album, track, compose(lines.sort { (e1, e2) => e1._1 > e2._1 }))
      case null => null
    }
}
