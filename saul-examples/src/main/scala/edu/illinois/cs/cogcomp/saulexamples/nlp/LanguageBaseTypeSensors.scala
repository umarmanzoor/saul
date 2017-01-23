package edu.illinois.cs.cogcomp.saulexamples.nlp

import java.util.Properties

import edu.illinois.cs.cogcomp.core.datastructures.{ViewNames, _}
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.{Constituent, TextAnnotation, TokenLabelView, TreeView}
import edu.illinois.cs.cogcomp.edison.features.FeatureUtilities
import edu.illinois.cs.cogcomp.edison.features.factory.{SubcategorizationFrame, WordFeatureExtractorFactory}
import edu.illinois.cs.cogcomp.nlp.common.PipelineConfigurator._
import edu.illinois.cs.cogcomp.nlp.utilities.CollinsHeadFinder
import edu.illinois.cs.cogcomp.saul.util.Logging
import edu.illinois.cs.cogcomp.saulexamples.nlp.BaseTypes._

import scala.collection.JavaConverters._
import scala.collection.mutable

/** Created by parisakordjamshidi on 12/25/16.
  */
object LanguageBaseTypeSensors extends Logging {
  private val dependencyView = ViewNames.DEPENDENCY_STANFORD
  private val parserView = ViewNames.PARSE_STANFORD
  private val sentenceById = mutable.HashMap[String, TextAnnotation]()
  private val settings = new Properties()
  TextAnnotationFactory.disableSettings(settings, USE_SRL_NOM, USE_NER_ONTONOTES, USE_SRL_VERB)
  private val as = TextAnnotationFactory.createPipelineAnnotatorService(settings)

  def documentToSentenceMatching(d: Document, s: Sentence): Boolean = {
    d.getId == s.getDocument.getId
  }

  def sentenceToPhraseGenerating(s: Sentence): Seq[Phrase] = {
    getPhrases(s)
  }

  def sentenceToPhraseMatching(s: Sentence, p: Phrase): Boolean = {
    s.getId == p.getSentence.getId
  }

  def phraseToTokenGenerating(p: Phrase): Seq[Token] = {
    getTokens(p)
  }

  def phraseToTokenMatching(p: Phrase, t: Token): Boolean = {
    p.getId == t.getPhrase.getId
  }

  def sentenceToTokenGenerating(s: Sentence): Seq[Token] = {
    getTokens(s)
  }

  def sentenceToTokenMatching(s: Sentence, t: Token): Boolean = {
    s.getId == t.getSentence.getId
  }

  def getPos(e: NlpBaseElement): Seq[String] = {
    val constituents = getElementConstituents(e)
    constituents.map(x => WordFeatureExtractorFactory.pos.getFeatures(x).asScala.mkString)
  }

  def getLemma(e: NlpBaseElement): Seq[String] = {
    val constituents = getElementConstituents(e)
    constituents.map(x => WordFeatureExtractorFactory.lemma.getFeatures(x).asScala.mkString)
  }

  def getHeadword(p: Phrase): Token = {
    var ta = getTextAnnotation(p)
    val (startId: Int, endId: Int) = getTextAnnotationSpan(p)
    var phrase = new Constituent("temp", "", ta, startId, endId + 1)
    var headId: Int = getHeadwordId(ta, phrase)
    if (headId < startId || headId > endId) {
      //when out of phrase, create a text annotation using just the phrase text
      ta = TextAnnotationFactory.createTextAnnotation(as, "", "", p.getText)
      phrase = new Constituent("temp", "", ta, 0, ta.getTokens.length)
      headId = getHeadwordId(ta, phrase)
    }
    val head = ta.getView(ViewNames.TOKENS).asInstanceOf[TokenLabelView].getConstituentAtToken(headId)
    new Token(p, p.getId + head.getSpan, head.getStartCharOffset, head.getEndCharOffset, head.toString)
  }

  def getHeadword(text: String): (String, Int, Int) = {
    val ta = TextAnnotationFactory.createTextAnnotation(as, "", "", text)
    val phrase = new Constituent("temp", "", ta, 0, ta.getTokens.length)
    val headId = getHeadwordId(ta, phrase)
    val head = ta.getView(ViewNames.TOKENS).asInstanceOf[TokenLabelView].getConstituentAtToken(headId)
    (head.toString, head.getStartCharOffset, head.getEndCharOffset)
  }

  def getDependencyRelation(t: Token): String = {
    val relations = getDependencyRelations(getTextAnnotation(t))
    val root = getDependencyRoot(relations)
    if (root != null && root.getStartCharOffset == t.getStart)
      "root"
    else
      relations.find(r => r.getTarget.getStartCharOffset == t.getStart) match {
        case Some(r) => r.getRelationName
        case _ => ""
      }
  }

  def getSemanticRole(e: NlpBaseElement): String = {
    val ta = getTextAnnotation(e)
    val view = if (ta.hasView(ViewNames.SRL_VERB)) {
      ta.getView(ViewNames.SRL_VERB)
    } else {
      logger.warn("Cannot find SRL view")
      null
    }
    val (startId: Int, endId: Int) = getTextAnnotationSpan(e)
    view match {
      case null => ""
      case _ => view.getLabelsCoveringSpan(startId, endId + 1).asScala.mkString(",")
    }
  }

  def isBefore(t1: Token, t2: Token): Boolean = {
    getStartTokenId(t1) < getStartTokenId(t2)
  }

  def getTokenDistance(t1: Token, t2: Token): Int = {
    Math.abs(getStartTokenId(t1) - getStartTokenId(t2))
  }

  def getCandidateRelations[T <: NlpBaseElement](argumentInstances: List[T]*): List[Relation] = {
    if (argumentInstances.length < 2) {
      List.empty
    } else {
      crossProduct(argumentInstances.seq.toList)
        .filter(args => args.filter(_ != null).groupBy {
          case x: Token => x.getSentence.getId
          case x: Phrase => x.getSentence.getId
          case x: Sentence => x.getDocument.getId
          case _ => null
        }.size <= 1 && args.filter(_ != null).groupBy(_.getId).size == args.count(_ != null))
        .map(args => {
          val r = new Relation()
          args.zipWithIndex.filter(x => x._1 != null).foreach {
            case (a, i) => {
              r.setArgumentId(i, a.getId)
              r.setId(r.getId + "[" + i + ", " + a.getId + "]")
            }
          }
          r
        })
    }
  }

  def crossProduct[T](input: List[List[T]]): List[List[T]] = input match {
    case Nil => Nil
    case head :: Nil => head.map(_ :: Nil)
    case head :: tail => for (elem <- head; sub <- crossProduct(tail)) yield elem :: sub
  }

  ////////////////////////////////////////////////////////////////////////////
  /// private methods
  ////////////////////////////////////////////////////////////////////////////
  private def getHeadwordId(ta: TextAnnotation, phrase: Constituent) = {
    val tree: TreeView = ta.getView(ViewNames.PARSE_STANFORD).asInstanceOf[TreeView]
    val parsePhrase = tree.getParsePhrase(phrase)
    val headId = CollinsHeadFinder.getInstance.getHeadWordPosition(parsePhrase)
    headId
  }

  def getSubCategorization(e: NlpBaseElement): String = {
    val (startId: Int, endId: Int) = getTextAnnotationSpan(e)
    val ta = getTextAnnotation(e)
    val v = ta.getView(ViewNames.TOKENS)
    val constituents = v.getConstituentsCoveringSpan(startId, endId + 1).asScala
    constituents
      .map(x => FeatureUtilities.getFeatureSet(new SubcategorizationFrame(ViewNames.PARSE_STANFORD), x)
        .asScala.mkString(",")).mkString(";")
  }

  private def getDependencyRoot(relations: Seq[textannotation.Relation]): Constituent = {
    relations.find(x => relations.count(r => r.getTarget == x.getSource) == 0) match {
      case Some(x) => x.getSource
      case _ => null
    }
  }

  private def getDependencyRelations(ta: TextAnnotation): Seq[textannotation.Relation] = {
    ta.getView(dependencyView).asInstanceOf[TreeView].getRelations.asScala
  }

  private def getSentence(e: NlpBaseElement) = e match {
    case s: Sentence => s
    case p: Phrase => p.getSentence
    case t: Token => t.getSentence
    case _ =>
      logger.warn("cannot use 'getSentence' for document type.")
      null
  }

  private def getPhrases(sentence: Sentence): Seq[Phrase] = {
    val ta = getTextAnnotation(sentence)
    val v = ta.getView(ViewNames.SHALLOW_PARSE)
    v.getConstituents.asScala.map(x =>
      new Phrase(sentence, generateId(sentence, x), x.getStartCharOffset, x.getEndCharOffset, x.toString))
  }

  private def getTokens(e: NlpBaseElement): Seq[Token] = {
    val sentence = getSentence(e)
    val ta = getTextAnnotation(sentence)
    val v = ta.getView(ViewNames.TOKENS)
    val (startId: Int, endId: Int) = getTextAnnotationSpan(e)
    v.getConstituentsCoveringSpan(startId, endId + 1).asScala.map(x =>
      e match {
        case p: Phrase => new Token(p, generateId(e, x), x.getStartCharOffset, x.getEndCharOffset, x.toString)
        case s: Sentence => new Token(s, generateId(e, x), x.getStartCharOffset, x.getEndCharOffset, x.toString)
        case _ =>
          logger.warn("cannot find tokens for base types other than phrase and sentence.")
          null
      }
    )
  }

  private def generateId(e: NlpBaseElement, x: Constituent): String = {
    e.getId + x.getSpan
  }

  private def getElementConstituents(e: NlpBaseElement): Seq[Constituent] = {
    val s = getSentence(e)
    val ta = getTextAnnotation(s)
    val v = ta.getView(ViewNames.TOKENS)
    val startId = ta.getTokenIdFromCharacterOffset(e.getStart)
    val endId = ta.getTokenIdFromCharacterOffset(e.getEnd - 1)
    v.getConstituentsCoveringSpan(startId, endId + 1).asScala
  }

  private def getTextAnnotation(e: NlpBaseElement): TextAnnotation = {
    val sentence = getSentence(e)
    if (!sentenceById.contains(sentence.getId)) {
      val ta = TextAnnotationFactory.createTextAnnotation(as, sentence.getDocument.getId, sentence.getId, sentence.getText)
      sentenceById.put(sentence.getId, ta)
    }
    sentenceById(sentence.getId)
  }

  private def getTextAnnotationSpan(e: NlpBaseElement): (Int, Int) = {
    (getStartTokenId(e), getStartTokenId(e))
  }

  private def getStartTokenId(e: NlpBaseElement): Int = {
    val ta = getTextAnnotation(e)
    val start = e match {
      case _: Document | _: Sentence => 0
      case _ => e.getStart
    }
    ta.getTokenIdFromCharacterOffset(start)
  }

  private def getEndTokenId(e: NlpBaseElement): Int = {
    val ta = getTextAnnotation(e)
    val end = e match {
      case _: Document | _: Sentence => ta.getText.length
      case _ => e.getEnd
    }
    ta.getTokenIdFromCharacterOffset(end - 1)
  }
}