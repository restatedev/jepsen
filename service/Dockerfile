FROM node:18

WORKDIR /usr/src/app

COPY . .

RUN ["npm", "run", "build"]

EXPOSE 8000
CMD [ "node", "dist/app.js"]

