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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.internal

import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.Call
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.*
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.=!=
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.action.RequestWithDataCt
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.audit.AuditService
import uk.gov.hmrc.agentregistrationfrontend.config.AppConfig
import uk.gov.hmrc.agentregistrationfrontend.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.ApplicationFactory
import uk.gov.hmrc.agentregistrationfrontend.util.Errors
import uk.gov.hmrc.agentregistrationfrontend.util.RequestSupport.getCurrentSessionId

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class InitiateAgentApplicationController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  agentApplicationService: AgentApplicationService,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  appConfig: AppConfig,
  applicationFactory: ApplicationFactory,
  auditService: AuditService
)
extends FrontendController(mcc, actions):

  /** This endpoint is called by Government Gateway upon successful login.
    */
  def initiateAgentApplication(
    agentType: AgentType,
    businessType: BusinessType,
    userRole: UserRole
  ): Action[AnyContent] = actions
    .getMaybeApplicationForInitiation
    .ensure(
      condition =
        implicit request =>
          val isHmrcAsAgentEnrolmentAllocatedToGroup: Future[Boolean] = enrolmentStoreProxyConnector
            .queryEnrolmentsAllocatedToGroup(request.groupId)
            .map(!_.exists(e => e.service === appConfig.hmrcAsAgentEnrolment.key && e.state === "Activated"))
          isHmrcAsAgentEnrolmentAllocatedToGroup
      ,
      resultWhenConditionNotMet =
        implicit request =>
          val redirectUrl: String = appConfig.taxAndSchemeManagementToSelfServeAssignmentOfAsaEnrolment
          logger.info(s"No Application can be created. ${appConfig.hmrcAsAgentEnrolment} is already assigned to group ${request.groupId}. Redirecting to taxAndSchemeManagementToSelfServeAssignmentOfAsaEnrolment ($redirectUrl)")
          Redirect(redirectUrl)
    )
    .async:
      implicit request =>
        if agentType =!= AgentType.UkTaxAgent then Errors.notImplemented("only UkTaxAgent is supported for now") else ()
        val nextCheckIfApplicationHasGrsData: Call = AppRoutes.apply.internal.RefusalToDealWithController.check()
        val startGrsJourney: Call = AppRoutes.apply.internal.GrsController.startJourney()

        request.get[Option[AgentApplication]] match
          case Some(application) if application.isGrsDataReceived =>
            logger.info("Application already has GRS data, redirecting to next check")
            Future.successful(Redirect(nextCheckIfApplicationHasGrsData))
          case Some(_) =>
            logger.info("Application found without GRS data, throwing away. Creating a new application and redirecting to GRS journey.")
            given RequestWithDataCt[AnyContent, DataWithAuth] = request.delete[Option[AgentApplication]]
            for {
              _ <- agentApplicationService.deleteAndStartAgain()
              _ <- startNewApplication(businessType, userRole)
            } yield Redirect(startGrsJourney)
          case None =>
            logger.info("No application found. Creating a new application and redirecting to GRS journey.")
            given RequestWithDataCt[AnyContent, DataWithAuth] = request.delete[Option[AgentApplication]]
            startNewApplication(businessType, userRole).map(_ => Redirect(startGrsJourney))

  extension (businessType: BusinessType)
    private def makeNewAgentApplication(
      userRole: UserRole,
      applicationReference: ApplicationReference
    )(using request: RequestWithDataCt[AnyContent, DataWithAuth]): AgentApplication = {
      val currentSessionId = getCurrentSessionId
      businessType match
        case BusinessType.Partnership.LimitedLiabilityPartnership =>
          applicationFactory.makeNewAgentApplicationLlp(
            internalUserId = request.internalUserId,
            cachedSessionId = currentSessionId,
            applicantCredentials = request.credentials,
            groupId = request.groupId,
            userRole = userRole,
            applicationReference = applicationReference
          )
        case BusinessType.SoleTrader =>
          applicationFactory.makeNewAgentApplicationSoleTrader(
            internalUserId = request.internalUserId,
            cachedSessionId = currentSessionId,
            applicantCredentials = request.credentials,
            groupId = request.groupId,
            userRole = userRole,
            applicationReference = applicationReference
          )
        case BusinessType.LimitedCompany =>
          applicationFactory.makeNewAgentApplicationLimitedCompany(
            internalUserId = request.internalUserId,
            cachedSessionId = currentSessionId,
            applicantCredentials = request.credentials,
            groupId = request.groupId,
            userRole = userRole,
            applicationReference = applicationReference
          )
        case BusinessType.Partnership.GeneralPartnership =>
          applicationFactory.makeNewAgentApplicationGeneralPartnership(
            internalUserId = request.internalUserId,
            cachedSessionId = currentSessionId,
            applicantCredentials = request.credentials,
            groupId = request.groupId,
            userRole = userRole,
            applicationReference = applicationReference
          )
        case BusinessType.Partnership.LimitedPartnership =>
          applicationFactory.makeNewAgentApplicationLimitedPartnership(
            internalUserId = request.internalUserId,
            cachedSessionId = currentSessionId,
            applicantCredentials = request.credentials,
            groupId = request.groupId,
            userRole = userRole,
            applicationReference = applicationReference
          )
        case BusinessType.Partnership.ScottishLimitedPartnership =>
          applicationFactory.makeNewAgentApplicationScottishLimitedPartnership(
            internalUserId = request.internalUserId,
            cachedSessionId = currentSessionId,
            applicantCredentials = request.credentials,
            groupId = request.groupId,
            userRole = userRole,
            applicationReference = applicationReference
          )
        case BusinessType.Partnership.ScottishPartnership =>
          applicationFactory.makeNewAgentApplicationScottishPartnership(
            internalUserId = request.internalUserId,
            cachedSessionId = currentSessionId,
            applicantCredentials = request.credentials,
            groupId = request.groupId,
            userRole = userRole,
            applicationReference = applicationReference
          )
    }

  private def startNewApplication(
    businessType: BusinessType,
    userRole: UserRole
  )(using request: RequestWithDataCt[AnyContent, DataWithAuth]): Future[Unit] =
    for {
      applicationReference <- agentApplicationService.generateNewApplicationReference()
      agentApplication: AgentApplication = businessType.makeNewAgentApplication(userRole, applicationReference)
      result <- agentApplicationService
        .upsert(agentApplication)
        .map(_ =>
          auditService.auditStartApplication(agentApplication)
        )
    } yield result
