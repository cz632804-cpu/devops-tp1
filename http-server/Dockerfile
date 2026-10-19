# Apache HTTP Server 2.4
FROM httpd:2.4

# Enable reverse proxy modules.
RUN sed -i \
    -e 's/^#LoadModule proxy_module/LoadModule proxy_module/' \
    -e 's/^#LoadModule proxy_http_module/LoadModule proxy_http_module/' \
    /usr/local/apache2/conf/httpd.conf

# Copy custom reverse proxy configuration.
COPY proxy.conf /usr/local/apache2/conf/extra/proxy.conf

# Load the additional configuration.
RUN echo "Include conf/extra/proxy.conf" \
    >> /usr/local/apache2/conf/httpd.conf

# HTTP port inside the container.
EXPOSE 80