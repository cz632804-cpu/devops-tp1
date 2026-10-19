# Apache HTTP Server 2.4
FROM httpd:2.4

# Enable the modules required for reverse proxying.
RUN sed -i \
    -e 's/^#LoadModule proxy_module/LoadModule proxy_module/' \
    -e 's/^#LoadModule proxy_http_module/LoadModule proxy_http_module/' \
    /usr/local/apache2/conf/httpd.conf

# Configure a global server name to avoid AH00558 warnings.
RUN echo "ServerName localhost" \
    >> /usr/local/apache2/conf/httpd.conf

# Copy the static landing page.
COPY index.html /usr/local/apache2/htdocs/index.html

# Copy the reverse proxy configuration.
COPY proxy.conf /usr/local/apache2/conf/extra/proxy.conf

# Load the custom Apache configuration.
RUN echo "Include conf/extra/proxy.conf" \
    >> /usr/local/apache2/conf/httpd.conf

EXPOSE 80