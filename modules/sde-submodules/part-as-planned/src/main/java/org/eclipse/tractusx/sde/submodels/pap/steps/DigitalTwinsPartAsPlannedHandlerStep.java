/********************************************************************************

 * Copyright (c) 2022, 2023 T-Systems International GmbH
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/
package org.eclipse.tractusx.sde.submodels.pap.steps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.tractusx.sde.common.constants.CommonConstants;
import org.eclipse.tractusx.sde.common.exception.CsvHandlerDigitalTwinUseCaseException;
import org.eclipse.tractusx.sde.common.exception.CsvHandlerUseCaseException;
import org.eclipse.tractusx.sde.common.submodel.executor.Step;
import org.eclipse.tractusx.sde.digitaltwins.entities.request.CreateSubModelRequest;
import org.eclipse.tractusx.sde.digitaltwins.entities.request.ShellDescriptorRequest;
import org.eclipse.tractusx.sde.digitaltwins.entities.request.ShellLookupRequest;
import org.eclipse.tractusx.sde.digitaltwins.entities.response.ShellDescriptorResponse;
import org.eclipse.tractusx.sde.digitaltwins.entities.response.SubModelListResponse;
import org.eclipse.tractusx.sde.digitaltwins.entities.response.SubModelResponse;
import org.eclipse.tractusx.sde.digitaltwins.facilitator.DigitalTwinsFacilitator;
import org.eclipse.tractusx.sde.digitaltwins.facilitator.DigitalTwinsUtility;
import org.eclipse.tractusx.sde.submodels.pap.model.PartAsPlanned;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@Service
@RequiredArgsConstructor
public class DigitalTwinsPartAsPlannedHandlerStep extends Step {

	private final DigitalTwinsFacilitator digitalTwinsFacilitator;

	private final DigitalTwinsUtility digitalTwinsUtility;

	@SneakyThrows
	public PartAsPlanned run(PartAsPlanned partAsPlannedAspect) throws CsvHandlerDigitalTwinUseCaseException {
		try {
			return doRun(partAsPlannedAspect);
		} catch (Exception e) {
			throw new CsvHandlerUseCaseException(partAsPlannedAspect.getRowNumber(),
					": DigitalTwins: " + e.getMessage());
		}
	}

	@SneakyThrows
	private PartAsPlanned doRun(PartAsPlanned partAsPlannedAspect) throws CsvHandlerDigitalTwinUseCaseException {
		ShellLookupRequest shellLookupRequest = getShellLookupRequest(partAsPlannedAspect);
		List<String> shellIds = digitalTwinsFacilitator.shellLookup(shellLookupRequest);

		String shellId;

		if (shellIds.isEmpty()) {
			logDebug(String.format("No shell id for '%s'", shellLookupRequest.toJsonString()));
			ShellDescriptorRequest aasDescriptorRequest = digitalTwinsUtility
					.getShellDescriptorRequest(getSpecificAssetIds(partAsPlannedAspect), partAsPlannedAspect);
			ShellDescriptorResponse result = digitalTwinsFacilitator.createShellDescriptor(aasDescriptorRequest);
			shellId = result.getIdentification();
			logDebug(String.format("Shell created with id '%s'", shellId));
		} else if (shellIds.size() == 1) {
			logDebug(String.format("Shell id found for '%s'", shellLookupRequest.toJsonString()));
			shellId = shellIds.stream().findFirst().orElse(null);
			
			digitalTwinsFacilitator.updateShellSpecificAssetIdentifiers(shellId,
					digitalTwinsUtility.getSpecificAssetIds(getSpecificAssetIds(partAsPlannedAspect), partAsPlannedAspect.getBpnNumbers()));
			
			logDebug(String.format("Shell id '%s'", shellId));
		} else {
			throw new CsvHandlerDigitalTwinUseCaseException(
					String.format("Multiple ids found on aspect %s", shellLookupRequest.toJsonString()));
		}

		partAsPlannedAspect.setShellId(shellId);
		SubModelListResponse subModelResponse = digitalTwinsFacilitator.getSubModels(shellId);
		SubModelResponse foundSubmodel = null;
		if (subModelResponse != null) {
			foundSubmodel = subModelResponse.getResult().stream().filter(x -> getIdShortOfModel().equals(x.getIdShort()))
					.findFirst().orElse(null);
			if (foundSubmodel != null)
				partAsPlannedAspect.setSubModelId(foundSubmodel.getId());
		}

		if (subModelResponse == null || foundSubmodel == null) {
			logDebug(String.format("No submodels for '%s'", shellId));
			CreateSubModelRequest createSubModelRequest = digitalTwinsUtility.getCreateSubModelRequest(
					partAsPlannedAspect.getShellId(), getsemanticIdOfModel(), getIdShortOfModel());
			digitalTwinsFacilitator.createSubModel(shellId, createSubModelRequest);
			partAsPlannedAspect.setSubModelId(createSubModelRequest.getId());
		} else {
			partAsPlannedAspect.setUpdated(CommonConstants.UPDATED_Y);
			logDebug("Complete Digital Twins Update Update Digital Twins");
		}

		return partAsPlannedAspect;
	}
	
	private ShellLookupRequest getShellLookupRequest(PartAsPlanned partAsPlannedAspect) {

		ShellLookupRequest shellLookupRequest = new ShellLookupRequest();
		getSpecificAssetIds(partAsPlannedAspect).entrySet().stream()
				.forEach(entry -> shellLookupRequest.addLocalIdentifier(entry.getKey(), entry.getValue()));

		return shellLookupRequest;
	}

	private Map<String, String> getSpecificAssetIds(PartAsPlanned partAsPlannedAspect) {
		Map<String, String> specificIdentifiers = new HashMap<>();
		specificIdentifiers.put(CommonConstants.MANUFACTURER_PART_ID, partAsPlannedAspect.getManufacturerPartId());
		specificIdentifiers.put(CommonConstants.MANUFACTURER_ID, digitalTwinsUtility.getManufacturerId());
		specificIdentifiers.put(CommonConstants.ASSET_LIFECYCLE_PHASE, CommonConstants.AS_PLANNED);

		return specificIdentifiers;
	}
}