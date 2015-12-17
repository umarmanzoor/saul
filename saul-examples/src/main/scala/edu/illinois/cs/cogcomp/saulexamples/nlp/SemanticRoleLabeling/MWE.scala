package edu.illinois.cs.cogcomp.saulexamples.nlp.SemanticRoleLabeling

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Relation
import edu.illinois.cs.cogcomp.saulexamples.nlp.SemanticRoleLabeling.SRLDataModel._

/** Created by Parisa on 12/15/15.
  */
object MWE extends App {

  populateGraphwithTextAnnotation(SRLDataModel, SRLDataModel.sentences)
  println("\n" + (relations(relations().head) ~> relationsToArguments).size)
  val x = new Relation("candidate", predicates().head, arguments().head, 0.0)
  println((relations(relations().head) ~> relationsToArguments).size)

  // Generate predicate candidates by extracting all verb tokens
  //  val predicateCandidates = tokens().filter((x: Constituent) => posTag(x).startsWith("VB"))
  //    .map(c => c.cloneForNewView(ViewNames.SRL_VERB))

  //  val negativePredicateCandidates = predicates(predicateCandidates)
  //    .filterNot(cand => (predicates() prop address).contains(address(cand)))

  //  predicates.populate(predicateCandidates)
  //
  //  val argumentCandidates = tokens().filter((x: Constituent) => posTag(x).startsWith("NN"))
  //    .map(c => c.cloneForNewView(ViewNames.SRL_VERB))

  //  val negativeArgumentCandidates = arguments(argumentCandidates)
  //    .filterNot(cand => (arguments() prop address).contains((arguments(cand) prop address).head))

  // arguments.populate(argumentCandidates)

  //  generate all candidate relations based on candidate arguments and predicates
  //  val relationCandidates2 = for {
  //    x <- predicates();
  //    y <- arguments()
  //  } yield new Relation("candidate", x, y, 0.0)

  // relations.populate(relationCandidates2)

  print("bye!")
}
