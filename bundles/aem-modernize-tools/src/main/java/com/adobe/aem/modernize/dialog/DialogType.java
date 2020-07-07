/*
 * AEM Modernize Tools
 *
 * Copyright (c) 2019 Adobe
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
 *
 */
package com.adobe.aem.modernize.dialog;

/**
 * Enumeration that defines the type of a dialog Node in the repository
 */
public enum DialogType {
    
    /**
     * Classic dialog
     */
    CLASSIC("Classic"),
    
    /**
     * Granite UI based dialog that uses (legacy) Coral 2 resource types
     */
    CORAL_2("Coral 2"),
    
    /**
     * Granite UI based dialog that uses Coral 3 resource types
     */
    CORAL_3("Coral 3"),
    
    /**
     * Dialog type is unknown
     */
    UNKNOWN("");
    
    private final String text;
    
    DialogType(String text) {
        this.text = text;
    }
    
    public String getString() {
        return text;
    }
    
}
