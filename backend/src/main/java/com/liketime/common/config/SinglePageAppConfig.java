package com.liketime.common.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Раздаёт собранный сайт (папка static внутри приложения). Адреса сайта вроде /groups или /salary/5
 * открываются обновлением страницы, поэтому для неизвестных путей отдаётся index.html, а React сам
 * показывает нужный экран. Пути /api и /actuator сюда не попадают и дают обычный 404.
 *
 * <p>Если сайт не собран и положен в приложение (обычная разработка), index.html нет и всё отвечает 404:
 * в разработке сайт открывается отдельно через Vite.
 */
@Configuration
public class SinglePageAppConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator")) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Файл с расширением (скрипт, картинка) не найден - это 404, а не страница сайта
                        if (resourcePath.contains(".")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
