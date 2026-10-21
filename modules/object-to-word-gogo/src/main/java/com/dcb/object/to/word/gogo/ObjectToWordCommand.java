package com.dcb.object.to.word.gogo;

import com.dcb.object.to.word.api.ObjectEntryWordExporter;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.io.File;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Gogo shell command to generate a MS Word document from an Object Entry.
 *
 * <p>
 * Usage: objectToWord:export companyId objectDefinitionERC objectEntryId
 * </p>
 *
 * <p>
 * Example: objectToWord:export 20097 ORDER 32674
 * </p>
 *
 * <p>
 * The template bundled in the module (/templates/{objectDefinitionERC}.docx)
 * is used and the document is saved in the app server temp folder
 * (object-to-word subfolder).
 * </p>
 *
 * @author Daniel Carrillo
 */
@Component(
	property = {
		"osgi.command.scope=objectToWord",
		"osgi.command.function=export"
	},
	service = ObjectToWordCommand.class
)
public class ObjectToWordCommand {

	public String export(
			long companyId, String objectDefinitionERC, long objectEntryId) {

		try {
			File file = _objectEntryWordExporter.export(
					companyId, objectDefinitionERC, objectEntryId);

			return "Word document generated: " + file.getAbsolutePath();
		}
		catch (Throwable throwable) {
			_log.error(throwable, throwable);

			return "Unable to generate the Word document: " + throwable;
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ObjectToWordCommand.class);

	@Reference
	private ObjectEntryWordExporter _objectEntryWordExporter;

}
