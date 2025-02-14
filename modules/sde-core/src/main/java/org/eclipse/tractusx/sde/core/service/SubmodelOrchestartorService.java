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

package org.eclipse.tractusx.sde.core.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.tractusx.sde.common.entities.SubmodelFileRequest;
import org.eclipse.tractusx.sde.common.entities.SubmodelJsonRequest;
import org.eclipse.tractusx.sde.common.entities.csv.CsvContent;
import org.eclipse.tractusx.sde.common.exception.ValidationException;
import org.eclipse.tractusx.sde.common.mapper.JsonObjectMapper;
import org.eclipse.tractusx.sde.common.mapper.SubmodelMapper;
import org.eclipse.tractusx.sde.common.model.Submodel;
import org.eclipse.tractusx.sde.common.submodel.executor.SubmodelExecutor;
import org.eclipse.tractusx.sde.common.validators.SubmodelCSVValidator;
import org.eclipse.tractusx.sde.core.csv.service.CsvHandlerService;
import org.eclipse.tractusx.sde.core.failurelog.FailureLogs;
import org.eclipse.tractusx.sde.core.processreport.ProcessReportUseCase;
import org.eclipse.tractusx.sde.core.processreport.model.ProcessReport;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmodelOrchestartorService {

	private static final String PROCESS_ID = "process_id";

	private static final String ROW_NUMBER = "row_number";

	private final SubmodelCSVValidator sumodelcsvValidator;

	private final SubmodelService submodelService;

	private final SubmodelMapper submodelMapper;

	private final JsonObjectMapper jsonObjectMapper;

	private final ProcessReportUseCase processReportUseCase;

	private final FailureLogs failureLogs;

	private final CsvHandlerService csvHandlerService;
	
	private final SubmodelCsvService submodelCsvService;
	
	ObjectMapper mapper = new ObjectMapper();

	public void processSubmodelCsv(SubmodelFileRequest submodelFileRequest, String processId, String submodel) {

		Submodel submodelSchemaObject = submodelService.findSubmodelByNameAsSubmdelObject(submodel);

		CsvContent csvContent = csvHandlerService.processFile(processId);
		List<String> columns = csvContent.getColumns();
		
		if(!sumodelcsvValidator.validate(submodelSchemaObject, columns)) {
			throw new ValidationException(String.format("Csv column header is not matching %s submodel", submodel));
		}

		processCsv(submodelFileRequest, processId, submodelSchemaObject, csvContent);

	}

	private void processCsv(SubmodelFileRequest submodelFileRequest, String processId, Submodel submodelSchemaObject, CsvContent csvContent) {
		
		Runnable runnable = () -> {
			processReportUseCase.startBuildProcessReport(processId, submodelSchemaObject.getId(),
					csvContent.getRows().size(), submodelFileRequest.getBpnNumbers(),
					submodelFileRequest.getTypeOfAccess(), submodelFileRequest.getUsagePolicies());

			AtomicInteger successCount = new AtomicInteger();
			AtomicInteger failureCount = new AtomicInteger();

			SubmodelExecutor executor = submodelSchemaObject.getExecutor();
			executor.init(submodelSchemaObject.getSchema());

			csvContent.getRows().parallelStream().forEach(rowjObj -> {
				try {
					ObjectNode newjObject = jsonObjectMapper.submodelFileRequestToJsonNodePojo(submodelFileRequest);
					newjObject.put(ROW_NUMBER, rowjObj.position());
					newjObject.put(PROCESS_ID, processId);
					executor.executeCsvRecord(rowjObj, newjObject, processId);
					// fetch by ID and check it if it is success then its updated.
					successCount.incrementAndGet();

				} catch (Exception e) {
					failureLogs.saveLog(processId, e.getMessage());
					failureCount.incrementAndGet();
				}
			});

			int updatedcount = executor.getUpdatedRecordCount(processId);
			successCount.set(successCount.get() - updatedcount);
			processReportUseCase.finishBuildProgressReport(processId, successCount.get(), failureCount.get(),
					updatedcount);
		};

		new Thread(runnable).start();
	}

	public void processSubmodel(SubmodelJsonRequest<ObjectNode> submodelJsonRequest, String processId,
			String submodel) {
		Submodel submodelSchemaObject = submodelService.findSubmodelByNameAsSubmdelObject(submodel);
		JsonObject submodelSchema = submodelSchemaObject.getSchema();

		List<ObjectNode> rowData = submodelJsonRequest.getRowData();

		Runnable runnable = () -> {

			AtomicInteger atInt = new AtomicInteger();
			AtomicInteger successCount = new AtomicInteger();
			AtomicInteger failureCount = new AtomicInteger();
			SubmodelExecutor executor = submodelSchemaObject.getExecutor();
			executor.init(submodelSchema);

			Map<String, Object> mps = new HashMap<>();
			mps.put("type_of_access", submodelJsonRequest.getTypeOfAccess());
			mps.put("bpn_numbers", submodelJsonRequest.getBpnNumbers());
			mps.put("usage_policies", submodelJsonRequest.getUsagePolicies());

			processReportUseCase.startBuildProcessReport(processId, submodelSchemaObject.getId(), rowData.size(),
					submodelJsonRequest.getBpnNumbers(), submodelJsonRequest.getTypeOfAccess(),
					submodelJsonRequest.getUsagePolicies());

			rowData.stream().forEach(obj -> {
				int andIncrement = atInt.incrementAndGet();
				obj.put(ROW_NUMBER, andIncrement);
				obj.put(PROCESS_ID, processId);
			});

			rowData.parallelStream().forEachOrdered(rowjObj -> {
				try {
					ObjectNode submodelJsonPojo = jsonObjectMapper.submodelJsonRequestToJsonPojo(rowjObj, mps);
					executor.executeJsonRecord(submodelJsonPojo.get(ROW_NUMBER).asInt(), submodelJsonPojo, processId);
					successCount.incrementAndGet();
				} catch (Exception e) {
					failureLogs.saveLog(processId, e.getMessage());
					failureCount.incrementAndGet();
				}
			});

			int updatedcount = executor.getUpdatedRecordCount(processId);
			successCount.set(successCount.get() - updatedcount);
			processReportUseCase.finishBuildProgressReport(processId, successCount.get(), failureCount.get(),
					updatedcount);
		};
		new Thread(runnable).start();
	}

	public void deleteSubmodelDigitalTwinsAndEDC(String refProcessId, String delProcessId, String submodel) {

		Submodel submodelSchema = submodelService.findSubmodelByNameAsSubmdelObject(submodel);
		AtomicInteger deletedCount = new AtomicInteger();
		AtomicInteger failureCount = new AtomicInteger();

		SubmodelExecutor executor = submodelSchema.getExecutor();

		ProcessReport oldProcessReport = processReportUseCase.getProcessReportById(refProcessId);

		List<JsonObject> readCreatedTwinsforDelete = executor.readCreatedTwinsforDelete(refProcessId);

		Runnable runnable = () -> {

			processReportUseCase.startDeleteProcess(oldProcessReport, refProcessId, submodel,
					readCreatedTwinsforDelete.size(), delProcessId);

			readCreatedTwinsforDelete.parallelStream().forEach(rowjObj -> {
				try {
					executor.executeDeleteRecord(rowjObj, delProcessId, refProcessId);
					deletedCount.incrementAndGet();
				} catch (Exception e) {
					failureLogs.saveLog(delProcessId, e.getMessage());
					failureCount.incrementAndGet();
				}
			});
			processReportUseCase.finishBuildDeleteProgressReport(delProcessId, deletedCount.get(), failureCount.get());
		};
		new Thread(runnable).start();

	}

	public Map<Object, Object> readCreatedTwinsDetails(String submodel, String uuid) {
		Submodel submodelSchema = submodelService.findSubmodelByNameAsSubmdelObject(submodel);
		SubmodelExecutor executor = submodelSchema.getExecutor();
		JsonObject readCreatedTwinsDetails = executor.readCreatedTwinsDetails(uuid);
		List<String> csvHeader = submodelCsvService.getCSVHeader(submodelSchema);
		JsonObject jElement = readCreatedTwinsDetails.get("csv").getAsJsonObject();
		JsonObject jObject=new JsonObject();
		for (String field : csvHeader) {
			jObject.add(field, jElement.get(field));
		}
		readCreatedTwinsDetails.add("csv", jObject);
		return submodelMapper.jsonPojoToMap(readCreatedTwinsDetails);
	}
	
	//New method of CSV process for Automation
	public void processSubmodelAutomationCsv(SubmodelFileRequest submodelFileRequest, String processId) {
		
		CsvContent csvContent = csvHandlerService.processFile(processId);
		List<String> columns = csvContent.getColumns();
		Submodel foundSubmodelSchemaObject = findSubmodel(columns);
		
		processCsv(submodelFileRequest, processId, foundSubmodelSchemaObject, csvContent);
	}

	public Submodel findSubmodel(List<String> columns) {
		
		Submodel foundSubmodelSchemaObject = null;
		List<Submodel> submodelDetails = submodelService.getAllSubmodels();
		for (Submodel submodel : submodelDetails) {
			
			if(sumodelcsvValidator.validate(submodel, columns)){
				foundSubmodelSchemaObject = submodel;
				break;
			}
		}

		if(foundSubmodelSchemaObject == null) {
			throw new ValidationException("Csv column header is not matching with any supported submodels");
		}
		return foundSubmodelSchemaObject;
	}
	
}

