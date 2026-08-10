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

package uk.gov.hmrc.agentregistrationfrontend.action.individual

import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.mvc.*
import play.api.mvc.Results.*
import sttp.model.Uri.UriContext
import uk.gov.hmrc.agentregistration.shared.InternalUserId
import uk.gov.hmrc.agentregistration.shared.Nino as ModelNino
import uk.gov.hmrc.agentregistration.shared.SaUtr
import uk.gov.hmrc.agentregistrationfrontend.action.individual.IndividualActions.*
import uk.gov.hmrc.agentregistrationfrontend.config.AppConfig
import uk.gov.hmrc.agentregistrationfrontend.controllers.AppRoutes
import uk.gov.hmrc.agentregistrationfrontend.util.Errors
import uk.gov.hmrc.agentregistrationfrontend.util.RequestAwareLogging
import uk.gov.hmrc.agentregistrationfrontend.util.RequestSupport.hc
import uk.gov.hmrc.agentregistrationfrontend.views.ErrorResults
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class IndividualAuthRefiner @Inject() (
  af: AuthorisedFunctions,
  errorResults: ErrorResults,
  appConfig: AppConfig
)(using ExecutionContext)
extends RequestAwareLogging:

  def refineIntoRequestWithAuth(
    request: RequestWithData[EmptyData]
  ): Future[Either[Result, RequestWithData[DataWithAuthAndCl]]] =
    given RequestWithData[EmptyData] = request

    af.authorised(
      AuthProviders(GovernmentGateway)
    ).retrieve(
      Retrievals.confidenceLevel
        and Retrievals.allEnrolments
        and Retrievals.internalId
        and Retrievals.credentials
        and Retrievals.affinityGroup
    ).apply:
      case cl ~ _ ~ maybeInternalId ~ maybeCredentials ~ maybeAffinityGroup =>
        maybeAffinityGroup match
          case Some(AffinityGroup.Agent) => Future.successful(Left(redirectToNotForAgentPage))
          case Some(AffinityGroup.Individual | AffinityGroup.Organisation) =>
            val internalUserId: InternalUserId = maybeInternalId.map(
              InternalUserId.apply
            ).getOrElse(Errors.throwServerErrorException("Retrievals for internalId is missing"))
            val credentials: Credentials = maybeCredentials.getOrElse(Errors.throwServerErrorException("Retrievals for credentials is missing"))
            Future.successful(Right(
              request
                .add(credentials)
                .add(internalUserId)
                .add(cl)
            ))
          case Some(unsupportedAffinityGroup) =>
            Future.successful(Left(errorResults.unauthorised(message = s"UnsupportedAffinityGroup: $unsupportedAffinityGroup")))
          case None => Future.successful(Left(errorResults.unauthorised(message = "AffinityGroup missing")))
    .recoverWith: e =>
      Future.successful(Left(recover(e)))

  def refineIntoRequestWithAdditionalIdentifiers(
    request: RequestWithData[EmptyData]
  ): Future[Either[Result, RequestWithData[DataWithAdditionalIdentifiers]]] =
    given RequestWithData[EmptyData] = request
    af.authorised(
      AuthProviders(GovernmentGateway)
    ).retrieve(
      Retrievals.confidenceLevel
        and Retrievals.allEnrolments
        and Retrievals.internalId
        and Retrievals.credentials
        and Retrievals.affinityGroup
    ).apply:
      case cl ~ allEnrolments ~ maybeInternalId ~ maybeCredentials ~ maybeAffinityGroup =>
        maybeAffinityGroup match
          case Some(AffinityGroup.Agent) => Future.successful(Left(redirectToNotForAgentPage))
          case Some(AffinityGroup.Individual | AffinityGroup.Organisation) =>
            val internalUserId: InternalUserId = maybeInternalId
              .map(InternalUserId.apply)
              .getOrElse(Errors.throwServerErrorException("Retrievals for internalId is missing"))
            val credentials: Credentials = maybeCredentials
              .getOrElse(Errors.throwServerErrorException("Retrievals for credentials is missing"))
            Future.successful(Right(
              request
                .add(credentials)
                .add(internalUserId)
                .add(cl)
                .add(getUtr(allEnrolments))
                .add(getNino(allEnrolments))
            ))
          case Some(unsupportedAffinityGroup) =>
            Future.successful(Left(errorResults.unauthorised(message = s"UnsupportedAffinityGroup: $unsupportedAffinityGroup")))
          case None => Future.successful(Left(errorResults.unauthorised(message = "AffinityGroup missing")))
    .recoverWith: e =>
      Future.successful(Left(recover(e)))

  private def redirectToNotForAgentPage(using request: RequestHeader): Result = Redirect(
    AppRoutes.providedetails.NotAgentCredentialController.show(
      Some(RedirectUrl(appConfig.thisFrontendBaseUrl + request.uri))
    )
  )

  private def recover(e: Throwable)(using request: RequestHeader): Result =
    e match
      case _: NoActiveSession =>
        logger.info(s"Unauthorised because of 'NoActiveSession', redirecting to sign in page")
        Redirect(url = appConfig.signInUri(uri"""${appConfig.thisFrontendBaseUrl + request.uri}""", AffinityGroup.Individual).toString())
      case e: UnsupportedAuthProvider =>
        logger.info(s"Unauthorised because of '${e.reason}', ${e.toString}")
        errorResults.unauthorised(message = e.reason)
      case e: UnsupportedAffinityGroup =>
        logger.info(s"Unauthorised because of '${e.reason}', ${e.toString}")
        redirectToNotForAgentPage
      case e: AuthorisationException =>
        logger.info(s"Unauthorised because of '${e.reason}', ${e.toString}")
        errorResults.unauthorised(message = e.toString)

  private def getIdentifierForEnrolmentKey(
    enrolmentKey: String,
    identifierKey: String
  )(using enrolments: Enrolments): Option[EnrolmentIdentifier] =
    for {
      enrolment <- enrolments.getEnrolment(enrolmentKey)
      identifier <- enrolment.getIdentifier(identifierKey)
    } yield identifier

  private def getNino(enrolments: Enrolments): Option[ModelNino] =
    given enr: Enrolments = enrolments

    val hmrcPtEnrolmentKey = "HMRC-PT"
    val hmrcNiEnrolmentKey = "HMRC-NI"
    val ninoIdentifierKey = "NINO"

    getIdentifierForEnrolmentKey(hmrcPtEnrolmentKey, ninoIdentifierKey)
      .orElse(getIdentifierForEnrolmentKey(hmrcNiEnrolmentKey, ninoIdentifierKey))
      .map(x => ModelNino(x.value))

  private def getUtr(enrolments: Enrolments): Option[SaUtr] =
    given enr: Enrolments = enrolments

    val hmrcPtEnrolmentKey = "IR-SA"
    val utrIdentifierKey = "UTR"

    getIdentifierForEnrolmentKey(hmrcPtEnrolmentKey, utrIdentifierKey)
      .map(x => SaUtr(x.value))
