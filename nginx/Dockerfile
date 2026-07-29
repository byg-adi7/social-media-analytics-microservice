# Render (and most container hosts) don't support the host bind-mount that
# docker-compose.yml uses for nginx.conf - bake it into the image instead.
FROM nginx:alpine
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
