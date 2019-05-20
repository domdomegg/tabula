import path from 'path';
import fs from 'fs';
import EventEmitter from 'events';
import WebpackNotifierPlugin from 'webpack-notifier';
import RemovePlugin from 'remove-files-webpack-plugin';
import {ProvidePlugin} from 'webpack';
import CopyWebpackPlugin from 'copy-webpack-plugin';
import MomentLocalesPlugin from 'moment-locales-webpack-plugin';

import StaticHashesPlugin from './build-tooling/StaticHashesPlugin';
import WatchEventsPlugin from './build-tooling/WatchEventsPlugin';

const merge = require('webpack-merge');
const tooling = require('./build-tooling/webpack.tooling');

const paths = {
  ROOT: __dirname,
  RELATIVE_ASSETS: 'build/rootContent',
  ASSETS: path.join(__dirname, 'build/rootContent'),
  NODE_MODULES: path.join(__dirname, 'node_modules/@universityofwarwick/id7'),
  ID7: path.join(__dirname, 'node_modules/@universityofwarwick/id7'),
  JS_ENTRY: {
    'static/js/id7/render': './src/main/assets/static/js/id7/render.js',
    'static/js/id7/home': './src/main/assets/static/js/id7/home.js',
    'static/js/id7/reports': './src/main/assets/static/js/id7/reports.js',
    'static/js/id7/admin': './src/main/assets/static/js/id7/admin.js',
    'static/js/id7/groups': './src/main/assets/static/js/id7/groups.js',
    'static/js/id7/exams': './src/main/assets/static/js/id7/exams.js',
    'static/js/id7/profiles': './src/main/assets/static/js/id7/profiles.js',
    'static/js/id7/cm2': './src/main/assets/static/js/id7/cm2.js',
    'static/js/id7/attendance': './src/main/assets/static/js/id7/attendance.js',
    'static/js/id7/mitcircs': './src/main/assets/static/js/id7/mitcircs.js',

    // Old ID6 junk
    'static/js/render': './src/main/assets/static/js/render.js',

    // Stuff that gets included from FTLs
    'static/js/sortable-table': './src/main/assets/static/js/sortable-table.js',
    'static/js/assignment-batch-select': './src/main/assets/static/js/assignment-batch-select.js',
    'static/js/textList': './src/main/assets/static/js/textList.js',
  },
  CSS_ENTRY: {
    'static/css/id7/render': './src/main/assets/static/css/id7/render.less',
    'static/css/id7/home': './src/main/assets/static/css/id7/home.less',
    'static/css/id7/reports': './src/main/assets/static/css/id7/reports.less',
    'static/css/id7/admin': './src/main/assets/static/css/id7/admin.less',
    'static/css/id7/groups': './src/main/assets/static/css/id7/groups.less',
    'static/css/id7/exams': './src/main/assets/static/css/id7/exams.less',
    'static/css/id7/profiles': './src/main/assets/static/css/id7/profiles.less',
    'static/css/id7/cm2': './src/main/assets/static/css/id7/cm2.less',
    'static/css/id7/attendance': './src/main/assets/static/css/id7/attendance.less',
    'static/css/id7/mitcircs': './src/main/assets/static/css/id7/mitcircs.less',

    // Old ID6 junk
    'static/css/bootstrap': './src/main/assets/static/css/bootstrap.less',
    'static/css/render': './src/main/assets/static/css/render.less',
  },
  CSS: './src/main/assets/static/css',
  IMAGES: './src/main/assets/static/images',
  FILES: './src/main/assets/static/files',
  FONTS: './src/main/assets/static/fonts',
  LIBS: './src/main/assets/static/libs',
  PUBLIC_PATH: '/',
};

let shimFontAwesomePro;
try {
  fs.accessSync(path.join(__dirname, 'node_modules/@fortawesome/fontawesome-pro/package.json'));
  shimFontAwesomePro = false;
} catch (err) {
  shimFontAwesomePro = true;
}

const commonConfig = merge([
  {
    output: {
      path: paths.ASSETS,
      publicPath: paths.PUBLIC_PATH,
    },
    node: {
      // Fix Webpack global CSP violation https://github.com/webpack/webpack/issues/6461
      global: false,
    },
    plugins: [
      // Fix Webpack global CSP violation https://github.com/webpack/webpack/issues/6461
      new ProvidePlugin({
        global: require.resolve('./build-tooling/global.js'),
      }),
      new RemovePlugin({
        before: {
          root: paths.ROOT,
          include: [`${paths.RELATIVE_ASSETS}/static`],
        },
        after: {
          root: paths.ROOT,
          test: [
            {
              folder: `${paths.RELATIVE_ASSETS}/static/css`,
              method: filePath => (new RegExp(/\.js.*$/, 'm').test(filePath)),
            },
          ],
        },
      }),
      new MomentLocalesPlugin({localesToKeep: ['en-gb']}),
    ],
    resolve: {
      alias: {
        id7: paths.ID7,
      },
    },
    externals: {
      // Provided by ID7
      jquery: 'jQuery',
    },
  },
  {
    plugins: shimFontAwesomePro ? [
      // Shim FontAwesome Pro if necessary
      new CopyWebpackPlugin([{
        from: 'src/main/assets/static/css/id7/fontawesome-pro-shim',
        to: path.join(__dirname, 'node_modules/@fortawesome/fontawesome-pro/less'),
      }]),
    ] : [],
  },
  tooling.lintJS(),
  tooling.transpileJS({
    entry: paths.JS_ENTRY,
    include: [
      /node_modules\/@universityofwarwick/,
      /src\/main\/assets\/static\/js/,
    ]
  }),
  tooling.copyNpmDistAssets({
    dest: path.join(paths.ASSETS, 'static/lib'),
    modules: ['@universityofwarwick/id7'],
  }),
  {
    plugins: [
      new CopyWebpackPlugin([{
        from: shimFontAwesomePro ? 'node_modules/@fortawesome/fontawesome-free/webfonts' : 'node_modules/@fortawesome/fontawesome-pro/webfonts',
        to: path.join(paths.ASSETS, 'static/lib/fontawesome-pro/webfonts'),
      }]),
    ],
  },
  tooling.copyAssets({
    src: paths.IMAGES,
    dest: `${paths.ASSETS}/static/images`,
  }),
  tooling.copyAssets({
    src: paths.FILES,
    dest: `${paths.ASSETS}/static/files`,
  }),
  tooling.copyAssets({
    src: paths.FONTS,
    dest: `${paths.ASSETS}/static/fonts`,
  }),
  tooling.copyAssets({
    src: paths.LIBS,
    dest: `${paths.ASSETS}/static/libs`,
  }),
  tooling.extractCSS({
    entry: paths.CSS_ENTRY,
    resolverPaths: [
      paths.NODE_MODULES,
    ],
  }),
  tooling.copyAssets({
    src: paths.CSS,
    dest: `${paths.ASSETS}/static/css`,
    test: new RegExp('.*\\.(css|htc|map)$', 'i'),
  }),
  {
    plugins: [
      new StaticHashesPlugin({
        base: 'static',
      }),
    ],
  },
]);

const productionConfig = merge([
  {
    mode: 'production',
  },
  tooling.transpileJS(),
  tooling.minify(),
  tooling.generateSourceMaps('source-map'),
]);

const developmentConfig = merge([
  {
    mode: 'development',
    plugins: [
      new WebpackNotifierPlugin(),
      new WatchEventsPlugin({emitter: new EventEmitter()}),
    ],
  },
  tooling.generateSourceMaps('cheap-module-source-map'),
]);

module.exports = ({production} = {}) => {
  if (production) {
    return merge(commonConfig, productionConfig);
  } else {
    return merge(commonConfig, developmentConfig);
  }
};
