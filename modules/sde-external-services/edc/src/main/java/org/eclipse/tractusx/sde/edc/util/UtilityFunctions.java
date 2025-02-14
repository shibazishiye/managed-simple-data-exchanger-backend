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

package org.eclipse.tractusx.sde.edc.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.tractusx.sde.common.entities.UsagePolicies;
import org.eclipse.tractusx.sde.common.enums.DurationEnum;
import org.eclipse.tractusx.sde.common.enums.PolicyAccessEnum;
import org.eclipse.tractusx.sde.common.enums.UsagePolicyEnum;
import org.eclipse.tractusx.sde.edc.entities.request.policies.ConstraintRequest;
import org.eclipse.tractusx.sde.edc.enums.Type;
import org.springframework.util.CollectionUtils;

public class UtilityFunctions {

	/*
	 * IMP: Resolve SonarQube Code smell Issue for
	 * "Add a private constructor to hide the implicit public one"
	 * 
	 */

	private UtilityFunctions() {
	}

	public static String removeLastSlashOfUrl(String url) {
		url = url.trim();
		if (url.endsWith("/")) {
			return url.substring(0, url.lastIndexOf("/"));
		} else {
			return url;
		}
	}

	public static UsagePolicies getDurationPolicy(String durationValue) {
		DurationEnum durationUnit;
		// Sample value - P0Y0M3DT0H0M0S - Output - 3 Day(s)
		String value = StringUtils.substringBetween(durationValue, "P", "Y");
		if (!value.equals("0")) {
			durationUnit = DurationEnum.YEAR;
			return getResponse(value, durationUnit);
		}
		value = StringUtils.substringBetween(durationValue, "Y", "M");
		if (!value.equals("0")) {
			durationUnit = DurationEnum.MONTH;
			return getResponse(value, durationUnit);
		}
		value = StringUtils.substringBetween(durationValue, "M", "D");
		if (!value.equals("0")) {
			durationUnit = DurationEnum.DAY;
			return getResponse(value, durationUnit);
		}
		value = StringUtils.substringBetween(durationValue, "T", "H");
		if (!value.equals("0")) {
			durationUnit = DurationEnum.HOUR;
			return getResponse(value, durationUnit);
		}
		value = StringUtils.substringBetween(durationValue, "H", "M");
		if (!value.equals("0")) {
			durationUnit = DurationEnum.MINUTE;
			return getResponse(value, durationUnit);
		}
		value = durationValue.substring(durationValue.lastIndexOf("M") + 1, durationValue.indexOf("S"));
		if (!value.equals("0")) {
			durationUnit = DurationEnum.SECOND;
			return getResponse(value, durationUnit);
		}
		return null;
	}

	private static UsagePolicies getResponse(String value, DurationEnum durationUnit) {
		UsagePolicies policyResponse;
		policyResponse = UsagePolicies.builder().type(UsagePolicyEnum.DURATION)
				.typeOfAccess(PolicyAccessEnum.RESTRICTED).value(value).durationUnit(durationUnit).build();
		return policyResponse;
	}

	public static List<UsagePolicies> getUsagePolicies(List<UsagePolicies> usagePolicies, List<ConstraintRequest> constraints) {
		constraints.forEach(constraint -> {
			String leftExpVal = constraint.getLeftOperand();
			String rightExpVal = constraint.getRightOperand().toString();
			UsagePolicies policyResponse = identyAndGetUsagePolicy(leftExpVal, rightExpVal);
			if (policyResponse != null)
				usagePolicies.add(policyResponse);
		});

		addMissingPolicies(usagePolicies);
		return usagePolicies;
	}

	public static UsagePolicies identyAndGetUsagePolicy(String leftExpVal, String rightExpVal) {

		UsagePolicies policyResponse = null;
		switch (leftExpVal) {
		case "idsc:ROLE":
			policyResponse = UsagePolicies.builder().type(UsagePolicyEnum.ROLE)
					.typeOfAccess(PolicyAccessEnum.RESTRICTED).value(rightExpVal).build();
			break;
		case "idsc:ELAPSED_TIME":
			policyResponse = UtilityFunctions.getDurationPolicy(rightExpVal);
			break;
		case "idsc:PURPOSE":
			policyResponse = UsagePolicies.builder().type(UsagePolicyEnum.PURPOSE)
					.typeOfAccess(PolicyAccessEnum.RESTRICTED).value(rightExpVal).build();
			break;
		default:
			break;
		}
		return policyResponse;
	}

	public static void addCustomUsagePolicy(Map<String, String> extensibleProperties,
			List<UsagePolicies> usagePolicies) {
		if (!CollectionUtils.isEmpty(extensibleProperties)
				&& extensibleProperties.keySet().contains(UsagePolicyEnum.CUSTOM.name())) {
			UsagePolicies policyObj = UsagePolicies.builder().type(UsagePolicyEnum.CUSTOM)
					.typeOfAccess(PolicyAccessEnum.RESTRICTED)
					.value(extensibleProperties.get(UsagePolicyEnum.CUSTOM.name())).build();
			usagePolicies.add(policyObj);
		} else {
			UsagePolicies policyObj = UsagePolicies.builder().type(UsagePolicyEnum.CUSTOM)
					.typeOfAccess(PolicyAccessEnum.UNRESTRICTED).value("").build();
			usagePolicies.add(policyObj);
		}
	}

	private static void addMissingPolicies(List<UsagePolicies> usagePolicies) {
		Arrays.stream(UsagePolicyEnum.values()).forEach(policy -> {
			if (!policy.equals(UsagePolicyEnum.CUSTOM)) {
				boolean found = usagePolicies.stream().anyMatch(usagePolicy -> usagePolicy.getType().equals(policy));
				if (!found) {
					UsagePolicies policyObj = UsagePolicies.builder().type(policy)
							.typeOfAccess(PolicyAccessEnum.UNRESTRICTED).value("").build();
					usagePolicies.add(policyObj);
				}
			}
		});
	}

	public static boolean checkTypeOfConnector(String type) {
		return StringUtils.isBlank(type) || Type.PROVIDER.name().equals(type);
	}

}
