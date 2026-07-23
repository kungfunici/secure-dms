FROM node:20-alpine

RUN apk add --no-cache libreoffice

WORKDIR /app
COPY server.js .

EXPOSE 3000
CMD ["node", "server.js"]
