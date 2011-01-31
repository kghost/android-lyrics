package name.kghost.android.lyrics.store

import scala.collection.mutable.LinkedList
import java.nio.CharBuffer
import name.kghost.android.lyrics.utils.With

private object Canonicalization {
  def map(s: CharSequence, f: (Char) => Char): CharSequence = With(CharBuffer.allocate(s.length)) { cb =>
    for (i <- Range(0, s.length)) cb.put(i, f(s.charAt(i)))
  }
}

trait Canonicalization extends Function1[CharSequence, CharSequence] {
  override def apply(s: CharSequence): CharSequence = s
}

object GlobalCanonicalization extends Canonicalization {
  trait Switch { var enable: Boolean }
  object RemoveBracket extends Switch with RemoveBracket { override var enable = true }
  private val trans: List[Switch with Canonicalization] = RemoveBracket :: Nil
  override def apply(s: CharSequence): CharSequence = {
    var r = s
    for (i <- trans)
      if (i.enable)
        r = i(r)
    r
  }
}

trait TrimSpace extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply(s.toString.trim)
}

trait TrimAllSpace extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply("""\s\s*""".r.replaceAllIn(s, ""))
}

trait TrimAllMarks extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply("""[~`!@#$%\^&*\(\)\-_+=|\\\{\}\[\]:\";\'<>\?,\./]""".r.replaceAllIn(s, ""))
}

trait TrimDupSpace extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply("""\s\s+""".r.replaceAllIn(s, " "))
}

trait UniqWhitespace extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply(Canonicalization.map(s, {
    case x if x.isWhitespace => ' '
    case x => x
  }))
}

trait ToLowerCase extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply(s.toString.toLowerCase)
}

trait ToUpperCase extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply(s.toString.toUpperCase)
}

// see http://www.unicode.org/charts/PDF/UFF00.pdf
trait FullWidthAsciiToHalfWidth extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply(Canonicalization.map(s, {
    case x if 0xFF01 <= x && x <= 0xFF5E => (x - 0xFEE0).toChar
    case x => x
  }))
}

trait HalfWidthKatakanaToFullWidth extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply(Canonicalization.map(s, {
    case '｡' => '。'
    case '｢' => '「'
    case '｣' => '」'
    case '､' => '、'
    case '･' => '・'
    case 'ｦ' => 'ヲ'
    case 'ｧ' => 'ァ'
    case 'ｨ' => 'ィ'
    case 'ｩ' => 'ゥ'
    case 'ｪ' => 'ェ'
    case 'ｫ' => 'ォ'
    case 'ｬ' => 'ャ'
    case 'ｭ' => 'ュ'
    case 'ｮ' => 'ョ'
    case 'ｯ' => 'ッ'
    case 'ｰ' => 'ー'
    case 'ｱ' => 'ア'
    case 'ｲ' => 'イ'
    case 'ｳ' => 'ウ'
    case 'ｴ' => 'エ'
    case 'ｵ' => 'オ'
    case 'ｶ' => 'カ'
    case 'ｷ' => 'キ'
    case 'ｸ' => 'ク'
    case 'ｹ' => 'ケ'
    case 'ｺ' => 'コ'
    case 'ｻ' => 'サ'
    case 'ｼ' => 'シ'
    case 'ｽ' => 'ス'
    case 'ｾ' => 'セ'
    case 'ｿ' => 'ソ'
    case 'ﾀ' => 'タ'
    case 'ﾁ' => 'チ'
    case 'ﾂ' => 'ツ'
    case 'ﾃ' => 'テ'
    case 'ﾄ' => 'ト'
    case 'ﾅ' => 'ナ'
    case 'ﾆ' => 'ニ'
    case 'ﾇ' => 'ヌ'
    case 'ﾈ' => 'ネ'
    case 'ﾉ' => 'ノ'
    case 'ﾊ' => 'ハ'
    case 'ﾋ' => 'ヒ'
    case 'ﾌ' => 'フ'
    case 'ﾍ' => 'ヘ'
    case 'ﾎ' => 'ホ'
    case 'ﾏ' => 'マ'
    case 'ﾐ' => 'ミ'
    case 'ﾑ' => 'ム'
    case 'ﾒ' => 'メ'
    case 'ﾓ' => 'モ'
    case 'ﾔ' => 'ヤ'
    case 'ﾕ' => 'ユ'
    case 'ﾖ' => 'ヨ'
    case 'ﾗ' => 'ラ'
    case 'ﾘ' => 'リ'
    case 'ﾙ' => 'ル'
    case 'ﾚ' => 'レ'
    case 'ﾛ' => 'ロ'
    case 'ﾜ' => 'ワ'
    case 'ﾝ' => 'ン'
    case 'ﾞ' => '゛'
    case 'ﾟ' => '゜'
    case x => x
  }))
}

trait RemoveBracket extends Canonicalization {
  override def apply(s: CharSequence): CharSequence = super.apply("""\([^)]*\)""".r.replaceAllIn(s, ""))
}
