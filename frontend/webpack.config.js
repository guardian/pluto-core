var webpack = require("webpack");
var path = require("path");
var TerserPlugin = require("terser-webpack-plugin");

var BUILD_DIR = path.resolve(__dirname, "../public/javascripts");
var APP_DIR = path.resolve(__dirname, "app");

var config = {
  entry: `${APP_DIR}/index.jsx`,
  output: {
    path: BUILD_DIR,
    filename: "bundle.js",
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx"],
  },
  optimization: {
    minimizer: [new TerserPlugin()],
  },
  module: {
    rules: [
      {
        test: /\.jsx?/,
        include: APP_DIR,
        loader: "ts-loader",
      },
      {
        test: /react-multistep\/.*\.js/,
        loader: "ts-loader",
      },
      {
        enforce: "pre",
        test: /\.js$/,
        exclude: /node_modules/,
        loader: "source-map-loader",
      },
      {
        test: /\.css$/,
        use: ["style-loader", "css-loader"],
      },
    ],
  },
  externals: {
    react: "React",
    "react-dom": "ReactDOM",
  },
  devtool: "source-map",
};

module.exports = config;
