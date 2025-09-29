package me.vripper.services;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import me.vripper.exception.XpathException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
public class XpathService {

  private final XPath xpath = XPathFactory.newInstance().newXPath();

  public Node getAsNode(Node source, String xpathExpression) throws XpathException {
    try {
      return (Node) xpath.compile(xpathExpression).evaluate(source, XPathConstants.NODE);
    } catch (Exception e) {
      throw new XpathException(e);
    }
  }

  public NodeList getAsNodeList(Node source, String xpathExpression) throws XpathException {
    try {
      return (NodeList) xpath.compile(xpathExpression).evaluate(source, XPathConstants.NODESET);
    } catch (Exception e) {
      throw new XpathException(e);
    }
  }
}
