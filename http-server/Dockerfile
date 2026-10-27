
# Build the Vue frontend provided by the teacher.
FROM node:18-bullseye AS frontend-build

WORKDIR /app

# Install Vue dependencies.
COPY devops-front/package.json ./
RUN npm install --registry=https://registry.npmjs.org/

# Build the Vue application.
COPY devops-front/ ./
ENV NODE_OPTIONS=--openssl-legacy-provider
RUN npm run build

# Use Apache to serve Vue and proxy API requests.
FROM httpd:2.4

RUN sed -i \
    -e 's/^#LoadModule proxy_module/LoadModule proxy_module/' \
    -e 's/^#LoadModule proxy_http_module/LoadModule proxy_http_module/' \
    -e 's/^#LoadModule rewrite_module/LoadModule rewrite_module/' \
    /usr/local/apache2/conf/httpd.conf

RUN echo "ServerName localhost" >> /usr/local/apache2/conf/httpd.conf

# Copy the compiled Vue application.
COPY --from=frontend-build /app/dist/ /usr/local/apache2/htdocs/

# Copy Apache reverse proxy configuration.
COPY http-server/proxy.conf /usr/local/apache2/conf/extra/proxy.conf

RUN echo "Include conf/extra/proxy.conf" >> /usr/local/apache2/conf/httpd.conf

EXPOSE 80
