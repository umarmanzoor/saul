/** This software is released under the University of Illinois/Research and Academic Use License. See
  * the LICENSE file in the root folder for details. Copyright (c) 2016
  *
  * Developed by: The Cognitive Computations Group, University of Illinois at Urbana-Champaign
  * http://cogcomp.cs.illinois.edu/
  */
package edu.illinois.cs.cogcomp.saulexamples.mSpRL2017

import edu.illinois.cs.cogcomp.saul.util.Logging
import edu.illinois.cs.cogcomp.saulexamples.data.CLEFImageReader
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.MultiModalConstrainedClassifiers.{LMPairConstraintClassifier, TRPairConstraintClassifier}
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.MultiModalSpRLClassifiers._
import edu.illinois.cs.cogcomp.saulexamples.nlp.BaseTypes._
import edu.illinois.cs.cogcomp.saulexamples.nlp.LanguageBaseTypeSensors._
import edu.illinois.cs.cogcomp.saulexamples.nlp.Xml.NlpXmlReader
import edu.illinois.cs.cogcomp.saulexamples.nlp.XmlMatchings
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.MultiModalSpRLDataModel._
import edu.illinois.cs.cogcomp.saulexamples.nlp.SpatialRoleLabeling.Dictionaries
import edu.illinois.cs.cogcomp.saulexamples.nlp.SpatialRoleLabeling.Eval.{RelationEval, RelationsEvalDocument, SpRLEvaluator}
import edu.illinois.cs.cogcomp.saulexamples.nlp.SpatialRoleLabeling.SpRL2013.SPATIALINDICATOR

import scala.collection.JavaConversions._

object imageApp extends App {

  val readFullData = false
  val CLEFDataset = new CLEFImageReader("data/mSprl/saiapr_tc-12", readFullData)

  val imageListTrain = CLEFDataset.trainingImages
  val segmentListTrain = CLEFDataset.trainingSegments
  val relationListTrain = CLEFDataset.trainingRelations

  images.populate(imageListTrain)
  segments.populate(segmentListTrain)
  segmentRelations.populate(relationListTrain)


  val imageListTest = CLEFDataset.testImages
  val segementListTest = CLEFDataset.testSegments
  val relationListTest = CLEFDataset.testRelations

  images.populate(imageListTest, false)
  segments.populate(segementListTest, false)
  segmentRelations.populate(relationListTest, false)

  ImageSVMClassifier.learn(5)
  ImageSVMClassifier.test(segementListTest)

  ImageClassifierWeka.learn(5)
  ImageClassifierWeka.test(segementListTest)
}

object textApp extends App with Logging {

  import MultiModalSpRLDataModel._

  val isTrain = true
  val path = if (isTrain) "data/SpRL/2017/clef/train/sprl2017_train.xml" else "data/SpRL/2017/clef/gold/sprl2017_gold.xml"
  val reader = new NlpXmlReader(path, "SCENE", "SENTENCE", null, null)
  reader.setIdUsingAnotherProperty("SCENE", "DOCNO")
  val documentList = reader.getDocuments()
  val sentenceList = reader.getSentences()

  documents.populate(documentList, isTrain)
  sentences.populate(sentenceList, isTrain)

  reader.addPropertiesFromTag("TRAJECTOR", tokens().toList, XmlMatchings.xmlHeadwordMatching)
  reader.addPropertiesFromTag("LANDMARK", tokens().toList, XmlMatchings.xmlHeadwordMatching)
  reader.addPropertiesFromTag("SPATIALINDICATOR", tokens().toList, XmlMatchings.xmlHeadwordMatching)

  SpatialRoleClassifier.modelDir = "models/mSpRL/spatialRole/"
  if (isTrain) {
    logger.info("training started ...")
    SpatialRoleClassifier.learn(100)
    SpatialRoleClassifier.save()
  }
  else {
    logger.info("testing started ...")
    SpatialRoleClassifier.load()
    SpatialRoleClassifier.test()
  }
}

object combinedApp extends App with Logging {

  val classifier = TrajectorRoleClassifier
  runClassifier(true)
  runClassifier(false)

  def runClassifier(isTrain: Boolean) = {
    combinedPairApp.populateData(isTrain)

    classifier.modelDir = "models/mSpRL/spatialRole/"
    if (isTrain) {
      println("training started ...")
      classifier.learn(50)
      classifier.save()
    }
    else {
      println("testing started ...")
      classifier.load()
      classifier.test()
    }
  }
}

object combinedPairApp extends App with Logging {

  import MultiModalSpRLDataModel._

  val classifiers = List(
    TrajectorRoleClassifier,
    LandmarkRoleClassifier,
    IndicatorRoleClassifier,
    TrajectorPairClassifier,
    LandmarkPairClassifier
  )

  //runClassifiers(true)
  runClassifiers(false)

  private def testTriplet(isTrain: Boolean): Unit = {
    val tokenInstances = if (isTrain) tokens.getTrainingInstances else tokens.getTestingInstances
    val indicators = tokenInstances.filter(t => IndicatorRoleClassifier(t) == "Indicator").toList.sortBy(x => x.getSentence.getStart + x.getStart)

    val relations = indicators.flatMap(sp => {
      val pairs = tokens(sp) <~ relationToSecondArgument
      val trajectorPairs = pairs.filter(r => TrajectorPairClassifier(r) == "TR-SP") ~> relationToFirstArgument
      if (trajectorPairs.nonEmpty) {
        val landmarkPairs = pairs.filter(r => LandmarkPairClassifier(r) == "LM-SP") ~> relationToFirstArgument
        if (landmarkPairs.nonEmpty) {
          trajectorPairs.flatMap(tr => landmarkPairs.map(lm => getRelationEval(tr, sp, lm))).toList
        }
        else {
          List()//trajectorPairs.map(t => getRelationEval(t, sp, null)).toList
        }
      }
      else {
        List()
      }
    })
    val predictedRelations = new RelationsEvalDocument(relations)
    val actualRelations = new RelationsEvalDocument(getActualRelationEvals(isTrain))
    val evaluator = new SpRLEvaluator()
    val results = evaluator.evaluateRelations(actualRelations, predictedRelations)
    evaluator.printEvaluation(results)
  }

  def getActualRelationEvals(isTrain: Boolean): List[RelationEval] = {
    val reader = getXmlReader(isTrain)
    val relations = reader.getRelations("RELATION", "trajector_id", "spatial_indicator_id", "landmark_id")
    val trajectors = reader.getTagAsNlpBaseElement("TRAJECTOR").map(x => x.getId -> x).toMap
    val landmarks = reader.getTagAsNlpBaseElement("LANDMARK").map(x => x.getId -> x).toMap
    // read sp as phrase in otder to have access to the sentence offset
    reader.setPhraseTagName("SPATIALINDICATOR")
    val indicators = reader.getPhrases().map(x => x.getId -> x).toMap
    relations.map(r => {
      val tr = trajectors(r.getArgumentId(0))
      val sp = indicators(r.getArgumentId(1))
      val lm = landmarks(r.getArgumentId(2))
      val offset = sp.getSentence.getStart
      new RelationEval(
        tr.getStart + offset,
        tr.getEnd + offset,
        sp.getStart + offset,
        sp.getEnd + offset,
        lm.getStart + offset,
        lm.getEnd + offset
      )
    }).toList
  }

  def getRelationEval(tr: Token, sp: Token, lm: Token): RelationEval = {
    val offset = sp.getSentence.getStart
    val lmStart = if (lm != null) offset + lm.getStart else -1
    val lmEnd = if (lm != null) offset + lm.getEnd else -1
    val trStart = if (tr != null) offset + tr.getStart else -1
    val trEnd = if (tr != null) offset + tr.getEnd else -1
    val spStart = offset + sp.getStart
    val spEnd = offset + sp.getEnd
    new RelationEval(trStart, trEnd, spStart, spEnd, lmStart, lmEnd)
  }

  private def runClassifiers(isTrain: Boolean) = {
    val (missingTr, missingLm) = populateData(isTrain, true)
    println("Missing trajector relations count: " + missingTr)
    println("Missing landmark relations count: " + missingLm)

    classifiers.foreach(_.modelDir = "models/mSpRL/spatialRole/")

    if (isTrain) {
      println("training started ...")

      classifiers.foreach(classifier => {
        classifier.learn(50)
        classifier.save()
      })
    }
    else {

      println("testing started ...")

      classifiers.foreach(classifier => {
        classifier.load()
        classifier.test()
      })
      testTriplet(isTrain)
      //TRPairConstraintClassifier.test()
      //LMPairConstraintClassifier.test()

    }
  }

  lazy val CLEFDataSet = new CLEFImageReader("data/mSprl/saiapr_tc-12", false)

  def populateData(isTrain: Boolean, populateRelations: Boolean = false) = {
    val reader: NlpXmlReader = getXmlReader(isTrain)
    val documentList = reader.getDocuments()
    val sentenceList = reader.getSentences()
    val imageList = if (isTrain) CLEFDataSet.trainingImages else CLEFDataSet.testImages
    val segmentList = if (isTrain) CLEFDataSet.trainingSegments else CLEFDataSet.testSegments
    val relationList = if (isTrain) CLEFDataSet.trainingRelations else CLEFDataSet.testRelations

    documents.populate(documentList, isTrain)
    sentences.populate(sentenceList, isTrain)
    images.populate(imageList, isTrain)
    segments.populate(segmentList, isTrain)
    segmentRelations.populate(relationList, isTrain)

    val tokenInstances = if (isTrain) tokens.getTrainingInstances else tokens.getTestingInstances

    reader.addPropertiesFromTag("TRAJECTOR", tokenInstances.toList, XmlMatchings.xmlHeadwordMatching)
    reader.addPropertiesFromTag("LANDMARK", tokenInstances.toList, XmlMatchings.xmlHeadwordMatching)
    reader.addPropertiesFromTag("SPATIALINDICATOR", tokenInstances.toList, XmlMatchings.xmlHeadwordMatching)

    if (populateRelations) {

      // read TRAJECTOR/LANDMARK elements and find empty ones: elements with `start` == -1
      val nullTrajectorIds = reader.getTagAsNlpBaseElement("TRAJECTOR").filter(_.getStart == -1).map(_.getId)
      val nullLandmarkIds = reader.getTagAsNlpBaseElement("LANDMARK").filter(_.getStart == -1).map(_.getId)

      // create pairs which first argument is trajector and second is indicator
      val goldTrajectorRelations = reader.getRelations("RELATION", "trajector_id", "spatial_indicator_id")
        .filter(x => !nullTrajectorIds.contains(x.getArgumentId(0)))
        .toList

      // create pairs which first argument is landmark and second is indicator
      val goldLandmarkRelations = reader.getRelations("RELATION", "landmark_id", "spatial_indicator_id")
        .filter(x => !nullLandmarkIds.contains(x.getArgumentId(0)))
        .toList

      val firstArgCandidates = tokenInstances.filter(x => getPos(x).head.contains("NN") || getPos(x).head.contains("PRP")).toList
      val spCandidates = tokenInstances.filter(x => getPos(x).head.contains("IN") || Dictionaries.isPreposition(x.getText)).toList

      val candidateRelations = getCandidateRelations(firstArgCandidates, spCandidates)

      candidateRelations.foreach(r => {
        val relationType = if (isGold(goldTrajectorRelations, r, "TRAJECTOR_id")) {
          "TR-SP"
        } else if (isGold(goldLandmarkRelations, r, "LANDMARK_id")) {
          "LM-SP"
        } else {
          "None"
        }
        r.setProperty("RelationType", relationType)
      })

      textRelations.populate(candidateRelations, isTrain)

      val missedTrSp = goldTrajectorRelations.size - candidateRelations.count(_.getProperty("RelationType") == "TR-SP")
      val missedLmSp = goldLandmarkRelations.size - candidateRelations.count(_.getProperty("RelationType") == "LM-SP")
      (missedTrSp, missedLmSp)
    }
    else {
      (0, 0)
    }
  }

  private def getXmlReader(isTrain: Boolean) = {
    val path = if (isTrain) "data/SpRL/2017/clef/train/sprl2017_train.xml" else "data/SpRL/2017/clef/gold/sprl2017_gold.xml"
    val reader = new NlpXmlReader(path, "SCENE", "SENTENCE", null, null)
    reader.setIdUsingAnotherProperty("SCENE", "DOCNO")
    reader
  }

  def isGold(goldRelations: List[Relation], r: Relation, firstArgName: String): Boolean = {
    goldRelations.exists(x =>
      r.getArgument(0).getPropertyValues(firstArgName).contains(x.getArgumentId(0)) &&
        r.getArgument(1).getPropertyValues("SPATIALINDICATOR_id").contains(x.getArgumentId(1))
    )
  }

}


