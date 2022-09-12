/********************************************************************************
 * Copyright (c) 2022 Critical TechWorks GmbH
 * Copyright (c) 2022 BMW GmbH
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

package com.catenax.dft.validators;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.catenax.dft.entities.usecases.Aspect;

public class AspectValidator implements ConstraintValidator<SubmodelValidation, Aspect> {
    @Override
    public void initialize(SubmodelValidation constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(Aspect aspect, ConstraintValidatorContext constraintValidatorContext) {
        if (!(aspect instanceof Aspect)) {
            throw new IllegalArgumentException("@AspectValidation only applies to Aspect objects");
        }

        String optionalIdentifierKey = aspect.getOptionalIdentifierKey();
        String optionalIdentifierValue = aspect.getOptionalIdentifierValue();

        return optionalIdentifierKey == null && optionalIdentifierValue == null
                || optionalIdentifierKey != null && optionalIdentifierValue != null;
    }
}
