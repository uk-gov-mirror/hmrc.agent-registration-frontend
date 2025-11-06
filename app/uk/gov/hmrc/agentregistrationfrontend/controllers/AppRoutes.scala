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

package uk.gov.hmrc.agentregistrationfrontend.controllers

import uk.gov.hmrc.agentregistrationfrontend.controllers.apply.routes as applyRoutes
import uk.gov.hmrc.agentregistrationfrontend.controllers.apply.aboutyourbusiness.routes as aboutyourbusinessRoutes
import uk.gov.hmrc.agentregistrationfrontend.controllers.apply.amls.routes as amlsRoutes
import uk.gov.hmrc.agentregistrationfrontend.controllers.apply.applicantcontactdetails.routes as applicantcontactdetailsRoutes
import uk.gov.hmrc.agentregistrationfrontend.controllers.apply.agentdetails.routes as agentdetailsRoutes
import uk.gov.hmrc.agentregistrationfrontend.controllers.apply.internal.routes as internalRoutes

import uk.gov.hmrc.agentregistrationfrontend.controllers.providedetails.routes as providedetailsRoutes

/** All application routes centralized in one place for convenience and clarity. It helps avoid naming conflicts and makes route management easier.
  *
  * Please add new controllers here.
  */
object AppRoutes:

  object apply:

    val AgentApplicationController = applyRoutes.AgentApplicationController
    val TaskListController = applyRoutes.TaskListController
    val SaveForLaterController = applyRoutes.SaveForLaterController

    object aboutyourbusiness:

      val AgentTypeController = aboutyourbusinessRoutes.AgentTypeController
      val TypeOfSignInController = aboutyourbusinessRoutes.TypeOfSignInController
      val PartnershipTypeController = aboutyourbusinessRoutes.PartnershipTypeController
      val BusinessTypeSessionController = aboutyourbusinessRoutes.BusinessTypeSessionController

    object amls:

      val AmlsExpiryDateController = amlsRoutes.AmlsExpiryDateController
      val CheckYourAnswersController = amlsRoutes.CheckYourAnswersController
      val AmlsSupervisorController = amlsRoutes.AmlsSupervisorController
      val AmlsEvidenceUploadController = amlsRoutes.AmlsEvidenceUploadController
      val AmlsRegistrationNumberController = amlsRoutes.AmlsRegistrationNumberController

    object applicantcontactdetails:

      val AuthorisedNameController = applicantcontactdetailsRoutes.AuthorisedNameController
      val CompaniesHouseMatchingController = applicantcontactdetailsRoutes.CompaniesHouseMatchingController
      val MemberNameController = applicantcontactdetailsRoutes.MemberNameController
      val CheckYourAnswersController = applicantcontactdetailsRoutes.CheckYourAnswersController
      val EmailAddressController = applicantcontactdetailsRoutes.EmailAddressController
      val TelephoneNumberController = applicantcontactdetailsRoutes.TelephoneNumberController
      val ApplicantRoleInLlpController = applicantcontactdetailsRoutes.ApplicantRoleInLlpController

    object agentdetails:

      val AgentBusinessNameController = agentdetailsRoutes.AgentBusinessNameController
      val AgentTelephoneNumberController = agentdetailsRoutes.AgentTelephoneNumberController
      val CheckYourAnswersController = agentdetailsRoutes.CheckYourAnswersController

    object internal:

      val InitiateAgentApplicationController = internalRoutes.InitiateAgentApplicationController
      val GrsController = internalRoutes.GrsController

  object providedetails:

    val StartController = providedetailsRoutes.StartController
    val LlpMemberNameController = providedetailsRoutes.LlpMemberNameController
    val CompaniesHouseMatchingController = providedetailsRoutes.CompaniesHouseMatchingController
