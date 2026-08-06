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

import com.softwaremill.quicklens.*
import play.api.data.Form
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.agentdetails.AgentBusinessName
import uk.gov.hmrc.agentregistration.shared.agentdetails.AgentDetails
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions

import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.forms.AgentBusinessNameForm
import uk.gov.hmrc.agentregistrationfrontend.services.BusinessPartnerRecordService
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService
import uk.gov.hmrc.agentregistrationfrontend.views.html.applicant.agentdetails.AgentBusinessNamePage

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class AgentBusinessNameController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  view: AgentBusinessNamePage,
  agentApplicationService: AgentApplicationService,
  businessPartnerRecordService: BusinessPartnerRecordService
)(using ec: ExecutionContext)
extends FrontendController(mcc, actions):

  def show: Action[AnyContent] = actions
    .getApplicationInProgress
    .getMaybeBusinessPartnerRecord
    .apply:
      implicit request =>
        Ok(view(
          form = AgentBusinessNameForm.form.fill:
            request
              .agentApplication
              .agentDetails.map(_.businessName)
          ,
          bprBusinessName = request.maybeBusinessPartnerRecordResponse.map(_.getEntityName)
        ))

  def submit: Action[AnyContent] =
    actions
      .getApplicationInProgress
      .ensureValidFormAndRedirectIfSaveForLater[AgentBusinessName](
        form = AgentBusinessNameForm.form,
        resultToServeWhenFormHasErrors =
          implicit request =>
            (formWithErrors: Form[AgentBusinessName]) =>
              businessPartnerRecordService
                .getBusinessPartnerRecord(
                  request.agentApplication.getUtr
                ).map: bprOpt =>
                  view(
                    form = formWithErrors,
                    bprBusinessName = bprOpt.map(_.getEntityName)
                  )
      )
      .async:
        implicit request =>
          val businessNameFromForm: AgentBusinessName = request.get
          val updatedApplication: AgentApplication = request
            .agentApplication
            .modify(_.agentDetails)
            .using:
              case None => // applicant enters agent details for first time
                Some(AgentDetails(
                  businessName = businessNameFromForm
                ))
              case Some(details) => // applicant updates agent business name
                Some(details
                  .modify(_.businessName)
                  .setTo(businessNameFromForm))
          agentApplicationService
            .upsert(updatedApplication)
            .map: _ =>
              Redirect(AppRoutes.apply.agentdetails.CheckYourAnswersController.show.url)
      .redirectIfSaveForLater
