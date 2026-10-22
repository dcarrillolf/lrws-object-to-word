package com.dcb.objecttoword.exporter.api;

import java.io.File;

public interface ObjectEntryWordExporter {

	public File export(
			long companyId, String objectDefinitionERC, long objectEntryId)
		throws Exception;

}
