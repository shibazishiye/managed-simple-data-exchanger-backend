/********************************************************************************
 * Copyright (c) 2022 T-Systems International GmbH
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.catenax.dft.entities.edc.request.policies;

import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class ObligationRequest {

    private String uid;
    private String target;
    private ActionRequest action;
    private String assignee;
    private String assigner;
    private ArrayList<ConstraintRequest> constraints;
    private String parentPermission;
    private String consequence;

    @SneakyThrows
    public String toJsonString() {
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(this);
    }
}
