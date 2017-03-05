/** This software is released under the University of Illinois/Research and Academic Use License. See
  * the LICENSE file in the root folder for details. Copyright (c) 2016
  *
  * Developed by: The Cognitive Computations Group, University of Illinois at Urbana-Champaign
  * http://cogcomp.cs.illinois.edu/
  */
package edu.illinois.cs.cogcomp.saulexamples.mSpRL2017

import java.io.{File, FileOutputStream}

import edu.illinois.cs.cogcomp.saul.util.Logging
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.Helpers.DataProportion._
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.Helpers.{FeatureSets, ImageReaderHelper, XmlReaderHelper, ReportHelper}
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.MultiModalConstrainedClassifiers.{LMPairConstraintClassifier, TRPairConstraintClassifier}
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.MultiModalPopulateData._
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.MultiModalSpRLClassifiers._
import edu.illinois.cs.cogcomp.saulexamples.mSpRL2017.MultiModalSpRLDataModel._
import org.apache.commons.io.FileUtils

object MultiModalSpRLApp extends App with Logging {

  MultiModalSpRLClassifiers.featureSet = FeatureSets.BaseLine
  MultiModalSpRLDataModel.useVectorAverages = false

  val classifiers = List(
    TrajectorRoleClassifier,
    LandmarkRoleClassifier,
    IndicatorRoleClassifier,
    TrajectorPairClassifier,
    LandmarkPairClassifier
  )
  val resultsDir = s"data/mSpRL/results/"
  val dataDir = "data/mSprl/saiapr_tc-12/"
  FileUtils.forceMkdir(new File(resultsDir))

  val suffix = if (useVectorAverages) "_vecAvg" else ""

  runClassifiers(true, ValidationTrain)
  runClassifiers(false, ValidationTest)

  private def runClassifiers(isTrain: Boolean, proportion: DataProportion) = {

    lazy val xmlReader = new XmlReaderHelper(dataDir, proportion)
    lazy val imageReader = new ImageReaderHelper(dataDir, proportion)

    populateDataFromAnnotatedCorpus(xmlReader, imageReader, isTrain, featureSet == FeatureSets.WordEmbeddingPlusImage)
    ReportHelper.saveCandidateList(isTrain,
      if (isTrain) pairs.getTrainingInstances.toList else pairs.getTestingInstances.toList)

    classifiers.foreach(x => {
      x.modelDir = s"models/mSpRL/$featureSet/"
      x.modelSuffix = suffix
    })

    if (isTrain) {
      println("training started ...")
      classifiers.foreach(classifier => {
        classifier.learn(50)
        classifier.save()
      })
    } else {
      println("testing started ...")
      val stream = new FileOutputStream(s"$resultsDir/$featureSet$suffix.txt")
      val allCandidateResults = TripletClassifierUtils.test(dataDir, resultsDir, featureSet.toString, isTrain, proportion,
        _ => "TR-SP",
        _ => "Indicator",
        _ => "LM-SP"
      )
      ReportHelper.saveEvalResults(stream, "triplet-all-candidates", allCandidateResults)

      classifiers.foreach(classifier => {
        classifier.load()
        val results = classifier.test()
        ReportHelper.saveEvalResults(stream, s"${classifier.getClassSimpleNameForClassifier}", results)
      })
      val results = TripletClassifierUtils.test(dataDir, resultsDir, featureSet.toString, isTrain, proportion,
        x => TrajectorPairClassifier(x),
        x => IndicatorRoleClassifier(x),
        x => LandmarkPairClassifier(x)
      )
      ReportHelper.saveEvalResults(stream, "triplet", results)

      /*Pair level constraints
      * */
      val trResults = TRPairConstraintClassifier.test()
      ReportHelper.saveEvalResults(stream, "TRPair-Constrained", trResults)

      val lmResults = LMPairConstraintClassifier.test()
      ReportHelper.saveEvalResults(stream, s"LMPair-Constrained", lmResults)

      val constrainedResults = TripletClassifierUtils.test(dataDir, resultsDir, featureSet.toString, isTrain, proportion,
        x => TRPairConstraintClassifier(x),
        x => IndicatorRoleClassifier(x),
        x => LMPairConstraintClassifier(x)
      )
      ReportHelper.saveEvalResults(stream, s"triplet-constrained", constrainedResults)

      /*Sentence level constraints
     * */

      val trSentenceResults = SentenceLevelConstraintClassifiers.TRConstraintClassifier.test()
      ReportHelper.saveEvalResults(stream, "TR-SentenceConstrained", trSentenceResults)

      val lmSentenceResults = SentenceLevelConstraintClassifiers.LMConstraintClassifier.test()
      ReportHelper.saveEvalResults(stream, "LM-SentenceConstrained", lmSentenceResults)

      val spSentenceResults = SentenceLevelConstraintClassifiers.IndicatorConstraintClassifier.test()
      ReportHelper.saveEvalResults(stream, "SP-SentenceConstrained", spSentenceResults)

      val trPairSentenceResults = SentenceLevelConstraintClassifiers.TRPairConstraintClassifier.test()
      ReportHelper.saveEvalResults(stream, "TRPair-SentenceConstrained", trPairSentenceResults)

      val lmPairSentenceResults = SentenceLevelConstraintClassifiers.LMPairConstraintClassifier.test()
      ReportHelper.saveEvalResults(stream, "LMPair-SentenceConstrained", lmPairSentenceResults)

      val constrainedPairSentenceResults = TripletClassifierUtils.test(dataDir, resultsDir, featureSet.toString, isTrain,
        proportion, x => SentenceLevelConstraintClassifiers.TRPairConstraintClassifier(x),
        x => SentenceLevelConstraintClassifiers.IndicatorConstraintClassifier(x),
        x => SentenceLevelConstraintClassifiers.LMPairConstraintClassifier(x)
      )
      ReportHelper.saveEvalResults(stream, "triplet-SentenceConstrained", constrainedPairSentenceResults)

      stream.close()
    }
  }

  //  val constrainedClassifiers =  List(
  //    SentenceLevelConstraintClassifiers.TRConstraintClassifier,
  //    SentenceLevelConstraintClassifiers.LMConstraintClassifier,
  //    SentenceLevelConstraintClassifiers.IndicatorConstraintClassifier,
  //    SentenceLevelConstraintClassifiers.TRPairConstraintClassifier,
  //    SentenceLevelConstraintClassifiers.LMPairConstraintClassifier)

  /*train classifier jointly*/
  // JointTrainSparseNetwork(sentences, constrainedClassifieList, 30, init = true)
  /*test the same list of constrainedclassifiers as before*/

}

