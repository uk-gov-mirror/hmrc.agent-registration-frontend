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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.agentdetails

import com.softwaremill.quicklens.each
import com.softwaremill.quicklens.modify
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.agentdetails.AgentCorrespondenceAddress
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.DesBusinessAddress
import uk.gov.hmrc.agentregistration.shared.companieshouse.ChroAddress
import uk.gov.hmrc.agentregistration.shared.getCompanyProfile
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.connectors.AddressLookupFrontendConnector
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.forms.AgentCorrespondenceAddressForm
import uk.gov.hmrc.agentregistrationfrontend.model.AddressOptions
import uk.gov.hmrc.agentregistrationfrontend.model.agentdetails.AgentCorrespondenceAddressHelper
import uk.gov.hmrc.agentregistrationfrontend.model.agentdetails.AgentCorrespondenceAddressHelper.toValueString
import uk.gov.hmrc.agentregistrationfrontend.services.BusinessPartnerRecordService
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService
import uk.gov.hmrc.agentregistrationfrontend.views.html.applicant.agentdetails.AgentCorrespondenceAddressPage

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class AgentCorrespondenceAddressController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  view: AgentCorrespondenceAddressPage,
  agentApplicationService: AgentApplicationService,
  businessPartnerRecordService: BusinessPartnerRecordService,
  addressLookUpConnector: AddressLookupFrontendConnector
)(using ec: ExecutionContext)
extends FrontendController(mcc, actions):

  private val baseAction: ActionBuilderWithData[DataWithApplication] = actions
    .getApplicationInProgress
    .ensure(
      _
        .agentApplication
        .agentDetails.exists(
          _.agentEmailAddress.exists(_.isVerified)
        ),
      implicit request =>
        logger.warn("Because we don't have a verified email address we are redirecting to the email address page")
        Redirect(AppRoutes.apply.agentdetails.AgentEmailAddressController.show)
    )

  def show: Action[AnyContent] = baseAction
    .getMaybeBusinessPartnerRecord
    .apply:
      implicit request =>
        val existingAddress: Option[AgentCorrespondenceAddress] = request
          .agentApplication
          .agentDetails
          .flatMap(
            _.agentCorrespondenceAddress
          )
        Ok(view(
          form = AgentCorrespondenceAddressForm.form.fill:
            existingAddress.map(_.toValueString)
          ,
          addressOptions = makeAddressOptions(
            agentApplication = request.agentApplication,
            bprOption = request.maybeBusinessPartnerRecordResponse.map(_.address)
          )
        ))

  def submit: Action[AnyContent] =
    baseAction
      .ensureValidFormAndRedirectIfSaveForLater[String](
        form = AgentCorrespondenceAddressForm.form,
        resultToServeWhenFormHasErrors =
          implicit request =>
            formWithErrors =>
              businessPartnerRecordService
                .getBusinessPartnerRecord(request.agentApplication.getUtr).map: bprOpt =>
                  view(
                    form = formWithErrors,
                    addressOptions = makeAddressOptions(
                      agentApplication = request.agentApplication,
                      bprOption = bprOpt.map(_.address)
                    )
                  )
      )
      .async:
        implicit request =>
          val addressOption: String = request.get
          if addressOption.matches("other")
          then
            addressLookUpConnector
              .initJourney(AppRoutes.apply.internal.AddressLookupCallbackController.journeyCallback(None))
              .map(Redirect(_))
          else
            val updatedApplication: AgentApplication = request
              .agentApplication
              .modify(_.agentDetails.each.agentCorrespondenceAddress)
              .setTo(Some(AgentCorrespondenceAddressHelper.fromValueString(addressOption)))
            agentApplicationService
              .upsert(updatedApplication)
              .map: _ =>
                Redirect(AppRoutes.apply.agentdetails.CheckYourAnswersController.show.url)
      .redirectIfSaveForLater

  /*
   * Determine whether to show an option for an "other" address on the form.
   * We infer the existing address to be "other" if it matches neither the CHRO address nor the BPR address.
   * This is could be because it originated from Address Lookup Frontend (ALF) in the first place, but it
   * also handles the case when either the CHRO or BPR address has changed since the last time we saved the application.
   */
  private def inferAnyOtherAddress(
    existingAddress: Option[AgentCorrespondenceAddress],
    bprAddress: Option[DesBusinessAddress],
    chroAddress: Option[ChroAddress]
  ): Option[AgentCorrespondenceAddress] =
    existingAddress match
      case Some(a: AgentCorrespondenceAddress) if a.toValueString === chroAddress.map(_.toValueString).getOrElse("") => None
      case Some(a: AgentCorrespondenceAddress) if a.toValueString === bprAddress.map(_.toValueString).getOrElse("") => None
      case Some(other: AgentCorrespondenceAddress) => Some(other)
      case _ => None

  private def makeAddressOptions(
    agentApplication: AgentApplication,
    bprOption: Option[DesBusinessAddress]
  ): AddressOptions =
    val chroAddressOption: Option[ChroAddress] =
      agentApplication match
        case _: AgentApplication.IsNotIncorporated => None
        case a: AgentApplication.IsIncorporated =>
          a
            .getCompanyProfile
            .unsanitisedCHROAddress

    /** The schema for CHRO addresses from Companies House allows for the postal code to be missing, but subscription requires a postal code for UK addresses -
      * it is optional in the subscription API but the rule is, if it's UK then it's required To work around this we treat CHRO addresses with missing postal
      * codes as invalid options for pre-filling.
      */
    val validChroAddressOption: Option[ChroAddress] =
      chroAddressOption match
        case Some(chroAddress) if chroAddress.postal_code.isEmpty => None
        case _ => chroAddressOption

    AddressOptions(
      chroAddress = validChroAddressOption,
      bprAddress = bprOption,
      otherAddress = inferAnyOtherAddress(
        existingAddress = agentApplication
          .agentDetails
          .flatMap(_.agentCorrespondenceAddress),
        bprAddress = bprOption,
        chroAddress = validChroAddressOption
      )
    )
