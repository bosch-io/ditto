# Ditto "Things Search" configuration extension file to be placed at /opt/ditto/search-extension.conf
ditto {
  {{- if .Values.thingsSearch.config.indexedFieldsLimiting.enabled }}
  extensions {
    caching-signal-enrichment-facade-provider = "org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchIndexingSignalEnrichmentFacadeProvider"
  }
  {{- end }}

  search {
    {{- if .Values.thingsSearch.config.indexedFieldsLimiting.enabled }}
    namespace-indexed-fields = [
      {{- range $index, $value := .Values.thingsSearch.config.indexedFieldsLimiting.items }}
      {
        namespace-pattern = "{{$value.namespacePattern}}"
        indexed-fields = [
        {{- range $fieldIndex, $indexedField := $value.indexedFields }}
         "{{$indexedField}}"
        {{- end }}
        ]
      }
      {{- end }}
    ]
    {{- end }}

    operator-metrics {
      custom-metrics {
        {{- range $cmKey, $cmValue := .Values.thingsSearch.config.operatorMetrics.customMetrics }}
        {{$cmKey}} = {
          enabled = {{$cmValue.enabled}}
          {{- if $cmValue.scrapeInterval }}
          scrape-interval = "{{$cmValue.scrapeInterval}}"
          {{- end }}
          namespaces = [
          {{- range $index, $namespace := $cmValue.namespaces }}
            "{{$namespace}}"
          {{- end }}
          ]
          filter = "{{$cmValue.filter}}"
          tags {
          {{- range $tagKey, $tagValue := $cmValue.tags }}
            {{$tagKey}} = "{{$tagValue}}"
          {{- end }}
          }
        }
        {{- end }}
      }

      custom-aggregation-metrics {
        {{- range $camKey, $camValue := .Values.thingsSearch.config.operatorMetrics.customAggregationMetrics }}
        {{$camKey}} = {
          enabled = {{$camValue.enabled}}
          {{- if $camValue.scrapeInterval }}
          scrape-interval = "{{$camValue.scrapeInterval}}"
          {{- end }}
          namespaces = [
          {{- range $index, $namespace := $camValue.namespaces }}
            "{{$namespace}}"
          {{- end }}
          ]
          group-by {
          {{- range $gbKey, $gbValue := $camValue.groupBy }}
            {{$gbKey}} = "{{$gbValue}}"
          {{- end }}
          }
          tags {
          {{- range $tagKey, $tagValue := $camValue.tags }}
            {{$tagKey}} = "{{$tagValue}}"
          {{- end }}
          }
          filters {
          {{- range $filterKey, $filterValue := $camValue.filters }}
            {{$filterKey}} {
              filter = "{{$filterValue.filter}}"
              inline-placeholder-values {
              {{- range $inlinePlaceholderKey, $inlinePlaceholderValue := $filterValue.inlinePlaceholderValues }}
                {{$inlinePlaceholderKey}} = "{{$inlinePlaceholderValue}}"
              {{- end }}
              }
            }
          {{- end }}
          }
        }
        {{- end }}
      }
    }
  }
}