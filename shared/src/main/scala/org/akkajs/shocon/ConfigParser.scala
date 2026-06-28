package org.akkajs.shocon

import fastparse._
import NoWhitespace._ // or we can refactor to use an upstream whitespace handler

object ConfigParser {
  case class NamedFunction[T, V](f: T => V, name: String) extends (T => V){
    def apply(t: T) = f(t)
    override def toString() = name
  }

  val isWhitespace = (c: Char) =>
    c match {
      // try to hit the most common ASCII ones first, then the nonbreaking
      // spaces that Java brokenly leaves out of isWhitespace.
      case ' '|'\n'|'\u00A0'|'\u2007'|'\u202F'|'\uFEFF' /* BOM */ => true;
      case _ => Character.isWhitespace(c);
    }

  val isWhitespaceNoNl = (c: Char) =>  c != '\n' && isWhitespace(c)

  // *** Lexing ***
  //  val Whitespace = NamedFunction(isWhitespace, "Whitespace")
  def letter[$: P]     = P( lowercase | uppercase )
  def lowercase[$: P]  = P( CharIn("a-z") )
  def uppercase[$: P]  = P( CharIn("A-Z") )
  def digit[$: P]      = P( CharIn("0-9") )

  val Digits = NamedFunction('0' to '9' contains (_: Char), "Digits")
  val StringChars = NamedFunction(!"\"\\".contains(_: Char), "StringChars")
  val UnquotedStringChars = NamedFunction(!isWhitespaceNoNl(_: Char), "UnquotedStringChars  ")

  def keyValueSeparator[$: P] = P( CharIn(":="))

  // whitespace
  def comment[$: P] = P( ("//" | "#") ~ CharsWhile(_ != '\n', 0) )
  def nlspace[$: P] = P( (CharsWhile(isWhitespace, 1) | comment ).rep )
  def space[$: P]   = P( ( CharsWhile(isWhitespaceNoNl, 1) | comment ).rep )

  def hexDigit[$: P] = P( CharIn("0-9", "a-f", "A-F") )
  def unicodeEscape[$: P]   = P( "u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit )
  def escape[$: P]          = P( "\\" ~ (CharIn("\"/\\bfnrt") | unicodeEscape) )

  // strings
  def strChars[$: P] = P( CharsWhile(StringChars) )
  def quotedString[$: P] = P( "\"" ~/ (strChars | escape).rep.! ~ "\"")
  def unquotedString[$: P] = P ( ( (letter | digit | "_" | "-" | "." | "/" | "%").rep(1).! ).rep(1,CharsWhile(_.isSpaceChar)).! )
  def string[$: P] = P(nlspace) ~ P(quotedString|unquotedString|CharsWhile(_.isSpaceChar).!) // bit of an hack: this would parse whitespace to the end of line
                            .rep(1).map(_.mkString.trim) // so we will trim the remaining right-side
                            .map(Config.StringLiteral.apply)

  // *** Parsing ***
  def array[$: P]: P[Seq[Config.Value]] = P( "[" ~ nlspace ~/ jsonExpr.rep(sep=itemSeparator) ~ nlspace ~ ",".? ~ nlspace ~ "]")

  def repeatedArray[$: P]: P[Config.Array] =
    array.rep(min = 1, sep=nlspace).map( ( arrays: Seq[Seq[Config.Value]] ) => Config.Array ( arrays.flatten ) )

  def pair[$: P]: P[(String, Config.Value)] = P( string.map(_.value) ~/ space ~
    ((keyValueSeparator   ~/ jsonExpr )
    |(repeatedObj ~ space)) )

  def obj[$: P]: P[Seq[(String, Config.Value)]] = P( "{" ~/ objBody ~ "}")

  def repeatedObj[$: P]: P[Config.Object] =
    obj.rep(min = 1, sep=nlspace).map(fields => Config.Object(Map( fields.flatten :_*) ))

  def itemSeparator[$: P] = P(("\n" ~ nlspace ~ ",".?)|(("," ~ nlspace).?))

  def objBody[$: P] = P( pair.rep(sep=itemSeparator) ~ nlspace ) // .log()

  def jsonExpr[$: P] = P( space ~ (repeatedObj | repeatedArray | string) ~ space ) // .log()

  def root[$: P] = P( (&(space ~ "{") ~/ obj )|(objBody)   ~ End ).map( x => Config.Object.fromPairs(x) ) // .log()

  def parseString(str: String) = parse(str, root(using _))

}
