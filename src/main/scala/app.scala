package com.github.philcali

import xsbti.{ AppMain, AppConfiguration }

import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.{
  File,
  FileInputStream,
  FileOutputStream => FOS
}

import java.net.URL
import util.control.Exception.allCatch

import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import xml.parsing.NoBindingFactoryAdapter
import org.xml.sax.InputSource

object Main {
  val Output = """\s*-o (.*\.pdf)\s*""".r

  def printHelp() {
    println("""
  spf v0.1.0, Copyright Philip Cali

  sdpf -h [file|folder|url...] [-o out.pdf]

  ex:
    spdf < curl http://google.com > out.pdf
    cat flyer.html | spdf -o flyer.pdf""")
  }

  def main(args: Array[String]) {
    run(args)
  }

  def run(args: Array[String]): Int = {
    val full = args.mkString(" ")
    val out = Output.findFirstMatchIn(full).map(_.group(1))

    if (args.contains("-h")) {
      printHelp()
    } else {
      val inputs = Output.replaceAllIn(full, "").split(" ")
      process(if (inputs.isEmpty) None else Some(inputs), out)
    }
    0
  }

  def isValid(src: Any) = allCatch.opt(src match {
    case url: URL => url.openConnection().getInputStream().close(); true
    case file: File => file.exists()
  }).getOrElse(false)

  def identify(str: String) = str match {
    case url if url.contains("://") => new URL(url)
    case file => new File(file)
  }

  def stream(src: Any) = src match {
    case url: URL => url.openStream()
    case file: File => new FileInputStream(file)
    case _ => throw new Exception("Unexpected source")
  }

  def toURL(src: Any) = src match {
    case url: URL => url.toString
    case file: File => file.toURI.toURL.toString
  }

  def buildDocument(is: java.io.InputStream) = {
      val adapter = new NoBindingFactoryAdapter
      val factory = new SAXFactoryImpl
      adapter.loadXML(new InputSource(is), factory.newSAXParser)
  }

  def flatten(src: Any): List[_] = src match {
    case dir: File if dir.isDirectory => dir.listFiles.filter(_.isFile).toList
    case other => List(other)
  }

  def paginate(src: Any, renderer: ITextRenderer) {
    val is = stream(src)

    try {
      renderer.setDocumentFromString(buildDocument(is).toString, toURL(src))
      renderer.layout()
      renderer.writeNextDocument()
    } finally {
      is.close()
    }
  }

  def process(in: Option[Array[String]], out: Option[String]) = {
    val os = out.map(new File(_)).map(new FOS(_)).getOrElse(Console.out)

    try {
      val renderer = new ITextRenderer

      val ids = in.map(_.map(identify).flatMap(flatten).filter(isValid)).filter(!_.isEmpty)

      val initial = ids.map(_.head).map(stream).getOrElse(System.in)
      val url = ids.map(_.head).map(toURL).getOrElse(toURL(new File(".")))

      renderer.setDocumentFromString(buildDocument(initial).toString, url)
      renderer.layout()
      renderer.createPDF(os, false)

      initial.close()

      // Write additional pages
      ids.map(_.tail.foreach(paginate(_, renderer)))

      renderer.finishPDF()

    } finally {
      os.close()
    }
  }
}

class Script extends AppMain {
  def run(conf: AppConfiguration) = Exit(Main.run(conf.arguments))
}

case class Exit(val code: Int) extends xsbti.Exit