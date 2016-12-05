/** This software is released under the University of Illinois/Research and Academic Use License. See
  * the LICENSE file in the root folder for details. Copyright (c) 2016
  *
  * Developed by: The Cognitive Computations Group, University of Illinois at Urbana-Champaign
  * http://cogcomp.cs.illinois.edu/
  */
package edu.illinois.cs.cogcomp.saulexamples.nlp.QuestionTypeClassification

import edu.illinois.cs.cogcomp.lbjava.learn.SparseNetworkLearner
import edu.illinois.cs.cogcomp.saul.classifier.Learnable
import edu.illinois.cs.cogcomp.saul.datamodel.property.Property
import edu.illinois.cs.cogcomp.saulexamples.nlp.QuestionTypeClassification.QuestionTypeClassificationDataModel._

object QuestionTypeClassificationClassifiers {

  abstract class TypeClassifier(properties: List[Property[QuestionTypeInstance]]) extends Learnable[QuestionTypeInstance](question) { }

  class CoarseFineTypeClassifier(properties: List[Property[QuestionTypeInstance]]) extends TypeClassifier(properties) {
    def label = QuestionTypeClassificationDataModel.bothLabel
    override def feature = using(properties)
    override lazy val classifier = new SparseNetworkLearner
  }

  class CoarseTypeClassifier(properties: List[Property[QuestionTypeInstance]]) extends TypeClassifier(properties) {
    def label = QuestionTypeClassificationDataModel.coarseLabel
    override def feature = using(properties)
    override lazy val classifier = new SparseNetworkLearner
  }

  class FineTypeClassifier(properties: List[Property[QuestionTypeInstance]]) extends TypeClassifier(properties) {
    def label = QuestionTypeClassificationDataModel.fineLabel
    override def feature = using(properties)
    override lazy val classifier = new SparseNetworkLearner
  }
}