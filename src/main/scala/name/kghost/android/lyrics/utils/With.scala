package name.kghost.android.lyrics.utils

case class With[T, R](obj: T) extends Function1[(T) => R, T] {
  override def apply(fun: (T) => R): T = { fun(obj); obj }
}
