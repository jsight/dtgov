/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.overlord.dtgov.ui.client.local.util;

import java.util.Date;

import org.jboss.errai.databinding.client.api.Converter;

import com.google.gwt.i18n.client.DateTimeFormat;

/**
 * Converts between a String and a Date (formatted with just the *time* portion).
 *
 * @author eric.wittmann@redhat.com
 */
public class DataBindingTimeConverter implements Converter<Date, String> {

    private static DateTimeFormat dateFormat;
    static {
        String dFormat = "h:mma";
        dateFormat = DateTimeFormat.getFormat(dFormat);
    }

    /**
     * Constructor.
     */
    public DataBindingTimeConverter() {
    }

    /**
     * @see org.jboss.errai.databinding.client.api.Converter#toModelValue(java.lang.Object)
     */
    @Override
    public Date toModelValue(String widgetValue) {
        try {
            return dateFormat.parse(widgetValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @see org.jboss.errai.databinding.client.api.Converter#toWidgetValue(java.lang.Object)
     */
    @Override
    public String toWidgetValue(Date modelValue) {
        if (modelValue == null)
            return "";
        else
            return dateFormat.format(modelValue);
    }

}
