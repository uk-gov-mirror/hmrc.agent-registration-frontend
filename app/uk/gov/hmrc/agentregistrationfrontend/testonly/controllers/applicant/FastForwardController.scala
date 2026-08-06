/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentregistrationfrontend.testonly.controllers.applicant

import play.api.mvc.*
import uk.gov.hmrc.agentregistration.shared.*
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetailsId
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetailsIdGenerator
import uk.gov.hmrc.agentregistration.shared.lists.*
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantAuthRefiner
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyData
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentRegistrationRiskingService
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.CompletedSection
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.PlanetId
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.SafeIdGenerator
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.UserId
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.withUpdatedIdentifiers
import uk.gov.hmrc.agentregistrationfrontend.testonly.services.GrsStubService
import uk.gov.hmrc.agentregistrationfrontend.testonly.services.StubUserService
import uk.gov.hmrc.agentregistrationfrontend.testonly.util.InternalUserIdGenerator
import uk.gov.hmrc.agentregistrationfrontend.testonly.views.html.FastForwardPage
import uk.gov.hmrc.agentregistrationfrontend.testsupport.testdata.TdTestOnly

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.chaining.scalaUtilChainingOps
import uk.gov.hmrc.agentregistrationfrontend.action.RequestWithDataCt
import uk.gov.hmrc.agentregistrationfrontend.testonly.connectors.TestAgentRegistrationConnector
import uk.gov.hmrc.auth.core.retrieve.Credentials

@Singleton
class FastForwardController @Inject() (
  mcc: MessagesControllerComponents,
  applicantActions: ApplicantActions,
  applicantAuthRefiner: ApplicantAuthRefiner,
  fastForwardPage: FastForwardPage,
  stubUserService: StubUserService,
  grsStubService: GrsStubService,
  agentApplicationIdGenerator: AgentApplicationIdGenerator,
  linkIdGenerator: LinkIdGenerator,
  agentRegistrationRiskingService: AgentRegistrationRiskingService,
  internalUserIdGenerator: InternalUserIdGenerator,
  individualProvidedDetailsIdGenerator: IndividualProvidedDetailsIdGenerator,
  testAgentRegistrationConnector: TestAgentRegistrationConnector,
  applicationReferenceGenerator: ApplicationReferenceGenerator,
  personReferenceGenerator: PersonReferenceGenerator
)(using
  clock: Clock,
  ex: ExecutionContext
)
extends FrontendController(mcc, applicantActions):

  def show: Action[AnyContent] = applicantActions
    .action:
      implicit request =>
        Ok(fastForwardPage())

  def fastForward(completedSection: CompletedSection): Action[AnyContent] = applicantActions
    .action
    .async:
      implicit req: RequestWithData[EmptyData] =>
        fastForwardApplicantTo(completedSection)
          .map(agentApplicationId => Redirect(AppRoutes.testOnly.applicant.TestOnlyController.showAgentApplicationTile(agentApplicationId)))

  private def fastForwardApplicantTo(section: CompletedSection)(using request: RequestWithData[EmptyData]): Future[AgentApplicationId] =

    val agentApplicationId: AgentApplicationId = agentApplicationIdGenerator.nextApplicationId()
    val planetId: PlanetId = PlanetId.make(agentApplicationId)
    val userIdApplicant: UserId = UserId.make(agentApplicationId)

    for
      userApplicant <- stubUserService.createUserApplicant(userIdApplicant, planetId)
      loginResponse <- stubUserService.signIn(userApplicant)
      loggedInAsUserApplicantRequest: RequestWithDataCt[AnyContent, EmptyTuple] = RequestWithDataCt.empty(
        loginResponse.refineRequest(request.request)
      )

      loggedInAsUserApplicantRequestWithAuthData: RequestWithData[DataWithAuth] <- applicantAuthRefiner
        .refine(loggedInAsUserApplicantRequest)
        .map:
          case Right(data: RequestWithData[DataWithAuth]) => data
          case Left(r) => throw new RuntimeException(s"ApplicantAuthRefiner didn't fetch DataWithAuth: $r")

      safeId = SafeIdGenerator.generateSafeId()
      agentApplication = section.agentApplication.withUpdatedIdentifiers(
        id = agentApplicationId,
        internalUserId = loggedInAsUserApplicantRequestWithAuthData.get[InternalUserId],
        linkId = linkIdGenerator.nextLinkId(),
        groupId = loggedInAsUserApplicantRequestWithAuthData.get[GroupId],
        applicationReference = applicationReferenceGenerator.generateApplicationReference(),
        createdAt = Instant.now(clock),
        safeId = safeId,
        // agents-external-stubs' /auth responses set credId/credentials.gatewayId to exactly the stub user's userId, so that's what providerId must match
        providerId = userIdApplicant.value
      )
      _ <- testAgentRegistrationConnector.upsertAgentApplication(agentApplication)
      _ <-
        grsStubService.storeStubsData(
          businessType = section.businessType,
          journeyData = journeyDataFor(section.businessType, safeId),
          deceased = false
        )(using loggedInAsUserApplicantRequest)

      individuals <- section
        .maybeIndividualProvidedDetailsList
        .getOrElse(Nil)
        .zipWithIndex
        .map: (t: (IndividualProvidedDetails, Int)) =>
          primeIndividual(
            template = t._1,
            agentApplicationId = agentApplication.agentApplicationId,
            individualName = getIndividualName(t._2)
          )
        .pipe(Future.sequence)
      _ <- sendForRiskingIfNeeded(agentApplication, individuals)(using loggedInAsUserApplicantRequestWithAuthData)
    yield agentApplicationId

  private def primeIndividual(
    template: IndividualProvidedDetails,
    agentApplicationId: AgentApplicationId,
    individualName: IndividualName
  )(using request: Request[?]): Future[IndividualProvidedDetails] =
    val individualProvidedDetailsId: IndividualProvidedDetailsId = individualProvidedDetailsIdGenerator.nextIndividualProvidedDetailsId()
    val planetId: PlanetId = PlanetId.make(agentApplicationId) // same across all individuals of this application
    val userIdIndividual: UserId = UserId.make(individualProvidedDetailsId) // new per individual (derived from the fresh ipdId)
    val individualProvidedDetails = template.copy(
      _id = individualProvidedDetailsId,
      personReference = personReferenceGenerator.generatePersonReference(),
      individualName = individualName,
      agentApplicationId = agentApplicationId,
      internalUserId = template.internalUserId.map(_ => internalUserIdGenerator.nextInternalUserId(userIdIndividual, planetId)),
      createdAt = Instant.now(clock)
    )
    for
      userIndividual <- stubUserService.createUserIndividual(
        userId = userIdIndividual,
        planetId = planetId,
        name = individualName.value
      )
      _ <- testAgentRegistrationConnector.upsertIndividualProvidedDetails(individualProvidedDetails)
    yield individualProvidedDetails

  private def sendForRiskingIfNeeded(
    agentApplication: AgentApplication,
    individuals: List[IndividualProvidedDetails]
  )(using request: Request[?]) =
    if agentApplication.applicationState.sentForRisking
    then
      agentRegistrationRiskingService.submitForRisking(
        agentApplication = agentApplication,
        individuals = individuals,
        arn = None,
        isResubmission = false,
        entityAlreadyApproved = false
      )
    else Future.unit

  private def getIndividualName(index: Int): IndividualName = TdTestOnly
    .individualNamesStubbedInCompaniesHouse
    .lift(index)
    .getOrThrowExpectedDataMissing(s"No identity stubbed at index $index")

  private def journeyDataFor(
    bt: BusinessType,
    safeId: SafeId
  ): JourneyData =
    val journeyData =
      bt match
        case BusinessType.Partnership.LimitedLiabilityPartnership => TdTestOnly.grsJourneyData.llp.journeyData
        case BusinessType.Partnership.GeneralPartnership => TdTestOnly.grsJourneyData.generalPartnership.journeyData
        case BusinessType.Partnership.ScottishPartnership => TdTestOnly.grsJourneyData.scottishPartnership.journeyData
        case BusinessType.Partnership.ScottishLimitedPartnership => TdTestOnly.grsJourneyData.scottishLtdPartnership.journeyData
        case BusinessType.Partnership.LimitedPartnership => TdTestOnly.grsJourneyData.ltdPartnership.journeyData
        case BusinessType.SoleTrader => TdTestOnly.grsJourneyData.soleTrader.journeyData
        case BusinessType.LimitedCompany => TdTestOnly.grsJourneyData.ltd.journeyData
    journeyData.copy(registration = journeyData.registration.copy(registeredBusinessPartnerId = Some(safeId)))
