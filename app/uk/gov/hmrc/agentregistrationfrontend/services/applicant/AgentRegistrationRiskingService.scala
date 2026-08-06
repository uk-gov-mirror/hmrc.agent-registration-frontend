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

package uk.gov.hmrc.agentregistrationfrontend.services.applicant

import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentregistration.shared.AgentApplication.IsIncorporated
import uk.gov.hmrc.agentregistration.shared.AgentApplication.IsNotIncorporated
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.ApplicationReference
import uk.gov.hmrc.agentregistration.shared.Arn
import uk.gov.hmrc.agentregistration.shared.getCrn
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.risking.RiskingProgress
import uk.gov.hmrc.agentregistration.shared.risking.submitforrisking.*
import uk.gov.hmrc.agentregistrationfrontend.audit.AuditService
import uk.gov.hmrc.agentregistrationfrontend.connectors.AgentRegistrationRiskingConnector
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentRegistrationRiskingServiceHelper.*
import uk.gov.hmrc.agentregistrationfrontend.util.RequestAwareLogging

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AgentRegistrationRiskingService @Inject() (
  agentRegistrationRiskingConnector: AgentRegistrationRiskingConnector,
  auditService: AuditService
)(using ExecutionContext)
extends RequestAwareLogging:

  def submitForRisking(
    agentApplication: AgentApplication,
    individuals: List[IndividualProvidedDetails],
    arn: Option[Arn],
    isResubmission: Boolean,
    entityAlreadyApproved: Boolean
  )(using request: RequestHeader): Future[Unit] =
    val submitForRiskingRequest: SubmitForRiskingRequest = SubmitForRiskingRequest(
      applicationData = makeApplicationData(agentApplication, arn),
      individuals = individuals.map(makeIndividualData),
      isResubmission = isResubmission,
      entityAlreadyApproved = entityAlreadyApproved
    )

    for
      _ <- agentRegistrationRiskingConnector.submitForRisking(submitForRiskingRequest)
      _ = auditService.sendRiskingSubmissionEvent(
        agentApplication,
        individuals,
        isResubmission
      )
    yield ()

  def getRiskingProgress(applicationReference: ApplicationReference)(using request: RequestHeader): Future[RiskingProgress] =
    agentRegistrationRiskingConnector.getRiskingProgressForApplicant(applicationReference)

object AgentRegistrationRiskingServiceHelper:

  def makeApplicationData(
    agentApplication: AgentApplication,
    arn: Option[Arn]
  ) = ApplicationData(
    applicationReference = agentApplication.applicationReference,
    internalUserId = agentApplication.internalUserId,
    applicantCredentials = agentApplication.applicantCredentials,
    businessType = agentApplication.businessType,
    groupId = agentApplication.groupId,
    applicantContactDetails = ApplicantContactDetailsData(
      applicantName = agentApplication.getApplicantContactDetails.applicantName,
      telephoneNumber = agentApplication.getApplicantContactDetails.getTelephoneNumber,
      applicantEmailAddress = agentApplication.getApplicantContactDetails.getVerifiedEmail
    ),
    amlsDetails = AmlsDetailsData(
      supervisoryBody = agentApplication.getAmlsDetails.supervisoryBody,
      amlsRegistrationNumber = agentApplication.getAmlsDetails.getAmlsRegistrationNumber,
      amlsEvidence = agentApplication.getAmlsDetails.amlsEvidence.map: amlsEvidence =>
        AmlsEvidenceData(
          fileUploadReference = amlsEvidence.fileUploadReference,
          fileName = amlsEvidence.fileName
        )
    ),
    agentDetails = AgentDetailsData(
      businessName = agentApplication.getAgentDetails.businessName,
      telephoneNumber = agentApplication.getAgentDetails.getTelephoneNumber,
      agentEmailAddress = agentApplication.getAgentDetails.getAgentEmailAddress.emailAddress.getEmailAddress,
      agentCorrespondenceAddress = agentApplication.getAgentDetails.getAgentCorrespondenceAddress
    ),
    vrns = agentApplication.getVrns,
    payeRefs = agentApplication.getPayeRefs,
    crn =
      agentApplication match
        case a: IsIncorporated => Some(a.getCrn)
        case _: IsNotIncorporated => None
    ,
    utr = agentApplication.getUtr,
    safeId = agentApplication.getSafeId,
    arn = arn
  )

  def makeIndividualData(i: IndividualProvidedDetails) = IndividualData(
    personReference = i.personReference,
    individualName = i.individualName,
    isPersonOfControl = i.isPersonOfControl,
    individualDateOfBirth = i.getIndividualDateOfBirth,
    telephoneNumber = i.getTelephoneNumber,
    emailAddress = i.getEmailAddress.emailAddress,
    individualNino = i.getNino,
    individualSaUtr = i.getIndividualSaUtr,
    vrns = i.getVrns,
    payeRefs = i.getPayeRefs,
    passedIv = i.getPassedIv,
    providedByApplicant = i.providedByApplicant.getOrElse(false)
  )
