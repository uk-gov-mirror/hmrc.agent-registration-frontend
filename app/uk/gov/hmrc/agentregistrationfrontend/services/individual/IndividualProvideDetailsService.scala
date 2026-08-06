/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentregistrationfrontend.services.individual

import com.softwaremill.quicklens.*
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentregistration.shared.*
import uk.gov.hmrc.agentregistration.shared.individual.IndividualDateOfBirth
import uk.gov.hmrc.agentregistration.shared.individual.IndividualNino.FromAuth
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetailsId
import uk.gov.hmrc.agentregistration.shared.individual.IndividualSaUtr
import uk.gov.hmrc.agentregistration.shared.individual.ProvidedDetailsState
import uk.gov.hmrc.agentregistration.shared.lists.IndividualName
import uk.gov.hmrc.agentregistrationfrontend.connectors.IndividualProvidedDetailsConnector
import uk.gov.hmrc.agentregistrationfrontend.model.citizendetails.CitizenDetails
import uk.gov.hmrc.agentregistrationfrontend.util.RequestAwareLogging

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class IndividualProvideDetailsService @Inject() (
  individualProvideDetailsConnector: IndividualProvidedDetailsConnector,
  provideDetailsFactory: IndividualProvideDetailsFactory,
  personReferenceGenerator: PersonReferenceGenerator
)(using ec: ExecutionContext)
extends RequestAwareLogging:

  def create(
    individualName: IndividualName,
    isPersonOfControl: Boolean,
    agentApplicationId: AgentApplicationId
  )(using request: RequestHeader): Future[IndividualProvidedDetails] = {
    generateNewPersonReference().map(personReference =>
      logger.info(s"creating provided details for individual with applicationId:[${agentApplicationId.value}] ")
      provideDetailsFactory.create(
        agentApplicationId,
        personReference,
        individualName,
        isPersonOfControl
      )
    )
  }

  def upsertForApplication(individualProvidedDetails: IndividualProvidedDetails)(using request: RequestHeader): Future[Unit] =
    logger.debug(s"Upserting providedDetails for user:[${individualProvidedDetails._id}] and applicationId:[${individualProvidedDetails.agentApplicationId}]")
    individualProvideDetailsConnector
      .upsert(individualProvidedDetails)

  def upsert(individualProvidedDetails: IndividualProvidedDetails)(using request: RequestHeader): Future[Unit] =
    logger.debug(s"Upserting providedDetails for user:[${individualProvidedDetails._id}] and applicationId:[${individualProvidedDetails.agentApplicationId}]")
    individualProvideDetailsConnector
      .upsertForIndividual(individualProvidedDetails)

  def upsert(listOfIndividualProvidedDetails: List[IndividualProvidedDetails])(using request: RequestHeader): Future[Unit] =
    logger.debug(s"Upserting providedDetails for ${listOfIndividualProvidedDetails.size} individuals")
    val upsertFutures: List[Future[Unit]] = listOfIndividualProvidedDetails.map: individualProvidedDetails =>
      logger.debug(s"Upserting new version of providedDetails for user:[${individualProvidedDetails._id}] and applicationId:[${individualProvidedDetails.agentApplicationId}]")
      individualProvideDetailsConnector.upsert(individualProvidedDetails)
    Future.sequence(upsertFutures).map: _ =>
      ()

  def findById(individualProvidedDetailsId: IndividualProvidedDetailsId)(using
    RequestHeader
  ): Future[Option[IndividualProvidedDetails]] = individualProvideDetailsConnector
    .findById(individualProvidedDetailsId)

  def delete(individualProvidedDetailsId: IndividualProvidedDetailsId)(using
    request: RequestHeader
  ): Future[Unit] =
    logger.debug(s"Deleting providedDetails for user:[${individualProvidedDetailsId.value}]")
    individualProvideDetailsConnector
      .delete(individualProvidedDetailsId)

  // for use by agent applicants when building lists of individuals
  def findAllKeyIndividualsByApplicationId(agentApplicationId: AgentApplicationId)(using request: RequestHeader): Future[List[IndividualProvidedDetails]] =
    findAllByApplicationId(agentApplicationId).map(_.filter(_.isPersonOfControl))

  def findAllOtherRelevantIndividualsByApplicationId(agentApplicationId: AgentApplicationId)(using
    request: RequestHeader
  ): Future[List[IndividualProvidedDetails]] = findAllByApplicationId(agentApplicationId).map(_.filterNot(_.isPersonOfControl))

  def findAllByApplicationId(agentApplicationId: AgentApplicationId)(using request: RequestHeader): Future[List[IndividualProvidedDetails]] =
    individualProvideDetailsConnector.findAll(agentApplicationId)

  //  for use by individuals when matching with an application - requires individual auth
  def findAllForMatchingWithApplication(agentApplicationId: AgentApplicationId)(using request: RequestHeader): Future[List[IndividualProvidedDetails]] =
    individualProvideDetailsConnector.findAllForMatching(agentApplicationId)

  def markLinkSent(individualProvidedDetailsList: List[IndividualProvidedDetails])(using request: RequestHeader): Future[Unit] = {
    // we only want to mark the link sent for provided details that have been precreated
    val upsertFutures: List[Future[Unit]] = individualProvidedDetailsList
      .filter(_.isPrecreated).map: individualProvidedDetails =>
        logger.debug(s"Marking link sent for providedDetails for user:[${individualProvidedDetails._id}] and applicationId:[${individualProvidedDetails.agentApplicationId}]")
        individualProvideDetailsConnector.upsert(
          individualProvidedDetails.copy(providedDetailsState = ProvidedDetailsState.AccessConfirmed)
        )

    Future.sequence(upsertFutures).map: _ =>
      ()
  }

  def claimIndividualProvidedDetails(
    individualProvidedDetails: IndividualProvidedDetails,
    internalUserId: InternalUserId,
    maybeNino: Option[Nino],
    citizenDetails: Option[CitizenDetails]
  )(using request: RequestHeader): Future[Unit] = {
    logger.debug(s"Claiming IndividualProvidedDetails for user:[${internalUserId.value}] and applicationId:[${individualProvidedDetails.agentApplicationId.value}]")
    individualProvideDetailsConnector
      .upsertForIndividual(
        individualProvidedDetails
          .modify(_.internalUserId)
          .setTo(Some(internalUserId))
          .modify(_.individualNino)
          .setTo(maybeNino.map(FromAuth(_))) // TODO: Should probably use a concrete nino given we expect CitizenDetails
          .modify(_.individualDateOfBirth)
          .setTo(citizenDetails.flatMap(_.dateOfBirth.map(IndividualDateOfBirth.FromCitizensDetails(_))))
          .modify(_.individualSaUtr)
          .setTo(citizenDetails.flatMap(_.saUtr.map(IndividualSaUtr.FromCitizenDetails(_))))
          .modify(_.providedDetailsState)
          .setTo(ProvidedDetailsState.Started)
      )
  }

  def claimIndividualNonCiDProvidedDetails(
    individualProvidedDetails: IndividualProvidedDetails,
    internalUserId: InternalUserId
  )(using request: RequestHeader): Future[Unit] = individualProvideDetailsConnector
    .upsertForIndividual(
      individualProvidedDetails
        .modify(_.internalUserId)
        .setTo(Some(internalUserId))
        .modify(_.providedDetailsState)
        .setTo(ProvidedDetailsState.Started)
    )

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def generateNewPersonReference()(using RequestHeader): Future[PersonReference] =
    val reference = personReferenceGenerator.generatePersonReference()
    individualProvideDetailsConnector.findByPersonReference(reference).flatMap:
      case Some(_) => generateNewPersonReference()
      case None => Future.successful(reference)
