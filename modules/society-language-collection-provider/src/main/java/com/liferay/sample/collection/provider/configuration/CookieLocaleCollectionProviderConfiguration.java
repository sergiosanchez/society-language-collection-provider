package com.liferay.sample.collection.provider.configuration;

import aQute.bnd.annotation.metatype.Meta;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * Configuración del Collection Provider. Se edita en
 * Panel de control > Configuración del sistema > Contenido web.
 *
 * Todo lo que puede cambiar entre entornos o en el futuro (nombre de la
 * cookie, correspondencia con categorías, estructura) vive aquí y no en el
 * código, de modo que un cambio de cookie no requiere un nuevo despliegue.
 */
@ExtendedObjectClassDefinition(
	category = "web-content",
	scope = ExtendedObjectClassDefinition.Scope.SYSTEM
)
@Meta.OCD(
	id = "com.liferay.sample.collection.provider.configuration.CookieLocaleCollectionProviderConfiguration",
	localization = "content/Language",
	name = "cookie-locale-collection-provider-configuration-name"
)
public interface CookieLocaleCollectionProviderConfiguration {

	@Meta.AD(
		deflt = "SOCIEDAD", description = "cookie-name-help",
		name = "cookie-name", required = false
	)
	public String cookieName();

	@Meta.AD(
		deflt = "", description = "cookie-value-mappings-help",
		name = "cookie-value-mappings", required = false
	)
	public String[] cookieValueMappings();

	@Meta.AD(
		deflt = "NOTICIA", description = "ddm-structure-key-help",
		name = "ddm-structure-key", required = false
	)
	public String ddmStructureKey();

	@Meta.AD(
		deflt = "title_", description = "language-field-prefix-help",
		name = "language-field-prefix", required = false
	)
	public String languageFieldPrefix();

	@Meta.AD(
		deflt = "false", description = "show-all-when-cookie-missing-help",
		name = "show-all-when-cookie-missing", required = false
	)
	public boolean showAllWhenCookieMissing();

}
