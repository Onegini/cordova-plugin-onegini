# Documentation
## Install Gitbook

For documentation we use [Gitbook](http://toolchain.gitbook.com). To install Gitbook and its dependencies we use [npm](https://docs.npmjs.com/).

Update your npm frequently with

```
npm install -g npm@latest
```
In case the latest version does not work you can try to use the following versions of the tools

```
node -v 6.11.0
npm -v 4.4.4
python 2.7.17
```

Run the following command in the `docs` folder to install or update Gitbook and its plugins.

```
npm install
```

## Run the documentation website

Run the following commands in the `docs` folder to run one of the documentation websites.

Public documentation:

```
npm run public
```

Internal documentation:

```
npm run internal
```

The site will now run on [http://localhost:4000](http://localhost:4000). 
Gitbook automatically refreshes the page in the browser after saving changes.

## Table of Content
In any markdown file, add `<!-- toc -->` where you want to add the TOC. 
For more information visit [gitbook-plugin-toc](https://github.com/whzhyh/gitbook-plugin-toc)
