package com.dcb.objecttoword.exporter.impl;

import com.dcb.objecttoword.exporter.api.ObjectEntryWordExporter;

import com.liferay.list.type.model.ListTypeEntry;
import com.liferay.list.type.service.ListTypeEntryLocalService;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.util.HtmlParserUtil;
import com.liferay.portal.kernel.util.SystemProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

import java.math.BigDecimal;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ObjectEntryWordExporter.class)
public class ObjectEntryWordExporterImpl implements ObjectEntryWordExporter {

	@Override
	public File export(
			long companyId, String objectDefinitionERC, long objectEntryId)
		throws Exception {

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					objectDefinitionERC, companyId);

		if (objectDefinition == null) {
			throw new PortalException(
				"No Object Definition found with ERC " + objectDefinitionERC +
					" in company " + companyId);
		}

		ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
			objectEntryId);

		if ((objectEntry == null) ||
			(objectEntry.getObjectDefinitionId() !=
				objectDefinition.getObjectDefinitionId())) {

			throw new PortalException(
				"No Object Entry found with ID " + objectEntryId +
					" for Object Definition " + objectDefinitionERC);
		}

		Map<String, String> values = _getValues(
			_companyLocalService.getCompany(companyId), objectDefinition,
			objectEntry);

		if (_log.isInfoEnabled()) {
			_log.info("Placeholder values: " + values);
		}

		String templatePath = _TEMPLATES_FOLDER + objectDefinitionERC + ".docx";

		File file = _getOutputFile(objectDefinitionERC, objectEntryId);

		try (InputStream inputStream =
				ObjectEntryWordExporterImpl.class.getResourceAsStream(
					templatePath)) {

			if (inputStream == null) {
				throw new PortalException(
					"No Word template found in the module at " + templatePath);
			}

			if (_log.isInfoEnabled()) {
				_log.info("Filling template " + templatePath);
			}

			try (OutputStream outputStream = new FileOutputStream(file)) {
				DocxTemplateFiller.fill(inputStream, outputStream, values);
			}
		}
		catch (Throwable throwable) {
			file.delete();

			throw throwable;
		}

		if (_log.isInfoEnabled()) {
			_log.info(
				"Generated " + file.getAbsolutePath() + " for Object Entry " +
					objectEntryId);
		}

		return file;
	}

	private String _formatValue(
			ObjectField objectField, Serializable value, Company company)
		throws PortalException {

		if (value == null) {
			return "";
		}

		String businessType = objectField.getBusinessType();

		if (value instanceof Date) {
			DateFormat dateFormat;

			if (ObjectFieldConstants.BUSINESS_TYPE_DATE.equals(businessType)) {
				dateFormat = new SimpleDateFormat(_DATE_PATTERN);

				dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			}
			else {
				dateFormat = new SimpleDateFormat(_DATE_TIME_PATTERN);

				dateFormat.setTimeZone(company.getTimeZone());
			}

			return dateFormat.format((Date)value);
		}

		if (value instanceof Boolean) {
			if ((Boolean)value) {
				return "Yes";
			}

			return "No";
		}

		if (value instanceof Double) {
			return BigDecimal.valueOf(
				(Double)value
			).stripTrailingZeros(
			).toPlainString();
		}

		if (value instanceof BigDecimal) {
			return ((BigDecimal)value).toPlainString();
		}

		String stringValue = String.valueOf(value);

		if (ObjectFieldConstants.BUSINESS_TYPE_PICKLIST.equals(businessType)) {
			return _getListTypeEntryName(
				objectField, stringValue, company.getLocale());
		}

		if (ObjectFieldConstants.BUSINESS_TYPE_MULTISELECT_PICKLIST.equals(
				businessType)) {

			List<String> names = new ArrayList<>();

			for (String key : stringValue.split("\\s*,\\s*")) {
				if (!key.isEmpty()) {
					names.add(
						_getListTypeEntryName(
							objectField, key, company.getLocale()));
				}
			}

			return String.join(", ", names);
		}

		if (ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT.equals(
				businessType)) {

			return HtmlParserUtil.extractText(stringValue);
		}

		return stringValue;
	}

	private String _getListTypeEntryName(
		ObjectField objectField, String key, Locale locale) {

		ListTypeEntry listTypeEntry =
			_listTypeEntryLocalService.fetchListTypeEntry(
				objectField.getListTypeDefinitionId(), key);

		if (listTypeEntry == null) {
			return key;
		}

		return listTypeEntry.getName(locale);
	}

	private File _getOutputFile(String objectDefinitionERC, long objectEntryId) {
		File folder = new File(
			SystemProperties.get(SystemProperties.TMP_DIR), "object-to-word");

		folder.mkdirs();

		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");

		String fileName =
			objectDefinitionERC.replaceAll("[^A-Za-z0-9_-]", "_") + "_" +
				objectEntryId + "_" + dateFormat.format(new Date()) + ".docx";

		return new File(folder, fileName);
	}

	private Map<String, String> _getValues(
			Company company, ObjectDefinition objectDefinition,
			ObjectEntry objectEntry)
		throws PortalException {

		Map<String, String> values = new HashMap<>();

		Map<String, Serializable> objectEntryValues = objectEntry.getValues();

		for (ObjectField objectField :
				_objectFieldLocalService.getObjectFields(
					objectDefinition.getObjectDefinitionId())) {

			if (!objectEntryValues.containsKey(objectField.getName())) {
				continue;
			}

			values.put(
				objectField.getName(),
				_formatValue(
					objectField, objectEntryValues.get(objectField.getName()),
					company));
		}

		DateFormat dateTimeFormat = new SimpleDateFormat(_DATE_TIME_PATTERN);

		dateTimeFormat.setTimeZone(company.getTimeZone());

		values.put("id", String.valueOf(objectEntry.getObjectEntryId()));
		values.put(
			"externalReferenceCode", objectEntry.getExternalReferenceCode());
		values.put("creator", objectEntry.getUserName());
		values.put(
			"createDate", dateTimeFormat.format(objectEntry.getCreateDate()));
		values.put(
			"modifiedDate",
			dateTimeFormat.format(objectEntry.getModifiedDate()));
		values.put("generatedDate", dateTimeFormat.format(new Date()));

		return values;
	}

	private static final String _DATE_PATTERN = "dd/MM/yyyy";

	private static final String _DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm";

	private static final String _TEMPLATES_FOLDER = "/templates/";

	private static final Log _log = LogFactoryUtil.getLog(
		ObjectEntryWordExporterImpl.class);

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private ListTypeEntryLocalService _listTypeEntryLocalService;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private ObjectFieldLocalService _objectFieldLocalService;

}
