package com.liferay.sample.collection.provider;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.info.collection.provider.CollectionQuery;
import com.liferay.info.collection.provider.FilteredInfoCollectionProvider;
import com.liferay.info.collection.provider.InfoCollectionProvider;
import com.liferay.info.collection.provider.SingleFormVariationInfoCollectionProvider;
import com.liferay.info.filter.CategoriesInfoFilter;
import com.liferay.info.filter.InfoFilter;
import com.liferay.info.pagination.InfoPage;
import com.liferay.info.pagination.Pagination;
import com.liferay.info.sort.Sort;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.search.BooleanClause;
import com.liferay.portal.kernel.search.BooleanClauseFactoryUtil;
import com.liferay.portal.kernel.search.BooleanClauseOccur;
import com.liferay.portal.kernel.search.Document;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.Hits;
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.IndexerRegistryUtil;
import com.liferay.portal.kernel.search.QueryConfig;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.filter.BooleanFilter;
import com.liferay.portal.kernel.search.filter.ExistsFilter;
import com.liferay.portal.kernel.search.generic.BooleanQueryImpl;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.sample.collection.provider.configuration.CookieLocaleCollectionProviderConfiguration;

import java.io.Serializable;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * Collection Provider que devuelve las noticias (JournalArticle de una
 * estructura concreta) filtradas por:
 *
 * <ul>
 *   <li>la sociedad del usuario, leída de una cookie y traducida a una
 *   categoría (por External Reference Code);</li>
 *   <li>el idioma de la petición: solo aparecen los artículos que tienen
 *   traducción en ese idioma (sin fallback al idioma por defecto).</li>
 * </ul>
 *
 * Sustituye a N segmentos x M idiomas con variaciones de colección por un
 * único proveedor que se usa en el fragmento Collection Display.
 *
 * Ambos filtros se aplican en la consulta al índice, de modo que la
 * paginación y el total de resultados son correctos.
 */
@Component(
	configurationPid = "com.liferay.sample.collection.provider.configuration.CookieLocaleCollectionProviderConfiguration",
	service = InfoCollectionProvider.class
)
public class CookieLocaleCollectionProvider
	implements FilteredInfoCollectionProvider<JournalArticle>,
			   SingleFormVariationInfoCollectionProvider<JournalArticle> {

	@Override
	public InfoPage<JournalArticle> getCollectionInfoPage(
		CollectionQuery collectionQuery) {

		ServiceContext serviceContext =
			ServiceContextThreadLocal.getServiceContext();

		if (serviceContext == null) {
			return _emptyInfoPage(collectionQuery);
		}

		try {
			long[] sociedadCategoryIds = _getSociedadCategoryIds(
				serviceContext);

			if ((sociedadCategoryIds == null) &&
				!_configuration.showAllWhenCookieMissing()) {

				return _emptyInfoPage(collectionQuery);
			}

			SearchContext searchContext = _buildSearchContext(
				collectionQuery, serviceContext, sociedadCategoryIds);

			Indexer<JournalArticle> indexer =
				IndexerRegistryUtil.nullSafeGetIndexer(JournalArticle.class);

			Hits hits = indexer.search(searchContext);

			List<JournalArticle> articles = new ArrayList<>();

			for (Document document : hits.getDocs()) {
				JournalArticle article =
					_journalArticleLocalService.fetchLatestArticle(
						GetterUtil.getLong(document.get(Field.GROUP_ID)),
						document.get(Field.ARTICLE_ID),
						WorkflowConstants.STATUS_APPROVED);

				if (article != null) {
					articles.add(article);
				}
			}

			return InfoPage.of(
				articles, collectionQuery.getPagination(), hits.getLength());
		}
		catch (Exception exception) {
			_log.error(exception);

			return _emptyInfoPage(collectionQuery);
		}
	}

	/**
	 * Devuelve el ID de la estructura como clave de variación, para que el
	 * Collection Display permita mapear los campos de la estructura. La
	 * estructura se resuelve por su clave (estable entre entornos), no por su
	 * ID.
	 */
	@Override
	public String getFormVariationKey() {
		DDMStructure ddmStructure = _getDDMStructure();

		if (ddmStructure == null) {
			return null;
		}

		return String.valueOf(ddmStructure.getStructureId());
	}

	@Override
	public String getLabel(Locale locale) {
		return _language.get(locale, "cookie-locale-collection-provider-label");
	}

	@Override
	public List<InfoFilter> getSupportedInfoFilters() {

		// Permite seguir combinando con el fragmento Collection Filter por
		// categorías (temática, etc.) además de la sociedad de la cookie

		return Collections.singletonList(new CategoriesInfoFilter());
	}

	@Activate
	@Modified
	protected void activate(Map<String, Object> properties) {
		_configuration = ConfigurableUtil.createConfigurable(
			CookieLocaleCollectionProviderConfiguration.class, properties);

		Map<String, String> cookieValueMappings = new HashMap<>();

		for (String mapping : _configuration.cookieValueMappings()) {
			int index = mapping.indexOf('=');

			if (index > 0) {
				cookieValueMappings.put(
					mapping.substring(0, index).trim(),
					mapping.substring(index + 1).trim());
			}
		}

		_cookieValueMappings = cookieValueMappings;
	}

	private SearchContext _buildSearchContext(
		CollectionQuery collectionQuery, ServiceContext serviceContext,
		long[] sociedadCategoryIds) {

		SearchContext searchContext = new SearchContext();

		searchContext.setAndSearch(true);

		DDMStructure ddmStructure = _getDDMStructure();

		Map<String, Serializable> attributes =
			HashMapBuilder.<String, Serializable>put(
				Field.STATUS, WorkflowConstants.STATUS_APPROVED
			).put(
				"head", Boolean.TRUE
			).put(
				"latest", Boolean.TRUE
			).build();

		if (ddmStructure != null) {
			attributes.put(
				"ddmStructureKey", ddmStructure.getStructureKey());

			searchContext.setClassTypeIds(
				new long[] {ddmStructure.getStructureId()});
		}

		searchContext.setAttributes(new HashMap<>(attributes));

		// Categorías: sociedad (cookie) + las que llegue del Collection Filter.
		// Con andSearch=true, el artículo debe tener todas ellas.

		long[] categoryIds = new long[0];

		if (sociedadCategoryIds != null) {
			categoryIds = ArrayUtil.append(categoryIds, sociedadCategoryIds);
		}

		CategoriesInfoFilter categoriesInfoFilter =
			collectionQuery.getInfoFilter(CategoriesInfoFilter.class);

		if (categoriesInfoFilter != null) {
			categoryIds = ArrayUtil.append(
				categoryIds,
				ArrayUtil.append(categoriesInfoFilter.getCategoryIds()));
		}

		if (categoryIds.length > 0) {
			searchContext.setAssetCategoryIds(ArrayUtil.unique(categoryIds));
		}

		// Idioma: solo artículos traducidos al idioma de la petición

		Locale locale = _getLocale(serviceContext);

		searchContext.setBooleanClauses(_getLanguageBooleanClauses(locale));
		searchContext.setLocale(locale);

		searchContext.setCompanyId(serviceContext.getCompanyId());
		searchContext.setEntryClassNames(
			new String[] {JournalArticle.class.getName()});
		searchContext.setGroupIds(
			new long[] {serviceContext.getScopeGroupId()});
		searchContext.setUserId(serviceContext.getUserId());

		Pagination pagination = collectionQuery.getPagination();

		if (pagination != null) {
			searchContext.setEnd(pagination.getEnd());
			searchContext.setStart(pagination.getStart());
		}

		Sort sort = collectionQuery.getSort();

		if (sort != null) {
			searchContext.setSorts(
				new com.liferay.portal.kernel.search.Sort(
					sort.getFieldName(),
					com.liferay.portal.kernel.search.Sort.LONG_TYPE,
					sort.isReverse()));
		}
		else {
			searchContext.setSorts(
				new com.liferay.portal.kernel.search.Sort(
					Field.DISPLAY_DATE,
					com.liferay.portal.kernel.search.Sort.LONG_TYPE, true));
		}

		QueryConfig queryConfig = searchContext.getQueryConfig();

		queryConfig.setHighlightEnabled(false);
		queryConfig.setScoreEnabled(false);

		return searchContext;
	}

	private InfoPage<JournalArticle> _emptyInfoPage(
		CollectionQuery collectionQuery) {

		return InfoPage.of(
			Collections.emptyList(), collectionQuery.getPagination(), 0);
	}

	private String _getCookieValue(HttpServletRequest httpServletRequest) {
		if (httpServletRequest == null) {
			return null;
		}

		Cookie[] cookies = httpServletRequest.getCookies();

		if (cookies == null) {
			return null;
		}

		for (Cookie cookie : cookies) {
			if (_configuration.cookieName().equals(cookie.getName())) {
				return URLDecoder.decode(
					cookie.getValue(), StandardCharsets.UTF_8);
			}
		}

		return null;
	}

	private DDMStructure _getDDMStructure() {
		ServiceContext serviceContext =
			ServiceContextThreadLocal.getServiceContext();

		if ((serviceContext == null) ||
			Validator.isNull(_configuration.ddmStructureKey())) {

			return null;
		}

		// includeAncestorStructures = true: encuentra también estructuras
		// definidas en el sitio Global o en sitios padre

		return _ddmStructureLocalService.fetchStructure(
			serviceContext.getScopeGroupId(),
			_portal.getClassNameId(JournalArticle.class),
			_configuration.ddmStructureKey(), true);
	}

	private BooleanClause[] _getLanguageBooleanClauses(Locale locale) {
		BooleanFilter booleanFilter = new BooleanFilter();

		// El campo title_<idioma> solo se indexa para los idiomas en los que
		// el artículo tiene título, es decir, en los que está traducido.
		// Verificar el nombre del campo en el índice de la versión del cliente
		// y ajustar el prefijo por configuración si fuese necesario.

		booleanFilter.add(
			new ExistsFilter(
				_configuration.languageFieldPrefix() +
					LocaleUtil.toLanguageId(locale)),
			BooleanClauseOccur.MUST);

		BooleanQueryImpl booleanQueryImpl = new BooleanQueryImpl();

		booleanQueryImpl.setPreBooleanFilter(booleanFilter);

		return new BooleanClause[] {
			BooleanClauseFactoryUtil.create(
				booleanQueryImpl, BooleanClauseOccur.MUST.getName())
		};
	}

	private Locale _getLocale(ServiceContext serviceContext) {
		Locale locale = serviceContext.getLocale();

		if (locale == null) {
			locale = LocaleUtil.getSiteDefault();
		}

		return locale;
	}

	/**
	 * Lee la cookie y devuelve la categoría de sociedad correspondiente, o
	 * null si no hay cookie o no se encuentra la categoría.
	 */
	private long[] _getSociedadCategoryIds(ServiceContext serviceContext)
		throws Exception {

		String cookieValue = _getCookieValue(serviceContext.getRequest());

		if (Validator.isNull(cookieValue)) {
			return null;
		}

		String externalReferenceCode = _cookieValueMappings.getOrDefault(
			cookieValue, cookieValue);

		// La categoría se busca en el sitio actual y, si no está, en Global

		AssetCategory assetCategory =
			_assetCategoryLocalService.fetchAssetCategoryByExternalReferenceCode(
				externalReferenceCode, serviceContext.getScopeGroupId());

		if (assetCategory == null) {
			Group companyGroup = _groupLocalService.getCompanyGroup(
				serviceContext.getCompanyId());

			assetCategory =
				_assetCategoryLocalService.
					fetchAssetCategoryByExternalReferenceCode(
						externalReferenceCode, companyGroup.getGroupId());
		}

		if (assetCategory == null) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"No se ha encontrado la categoría con ERC " +
						externalReferenceCode + " para el valor de cookie " +
							cookieValue);
			}

			return null;
		}

		return new long[] {assetCategory.getCategoryId()};
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CookieLocaleCollectionProvider.class);

	@Reference
	private AssetCategoryLocalService _assetCategoryLocalService;

	private volatile CookieLocaleCollectionProviderConfiguration _configuration;
	private volatile Map<String, String> _cookieValueMappings =
		Collections.emptyMap();

	@Reference
	private DDMStructureLocalService _ddmStructureLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private JournalArticleLocalService _journalArticleLocalService;

	@Reference
	private Language _language;

	@Reference
	private Portal _portal;

}
