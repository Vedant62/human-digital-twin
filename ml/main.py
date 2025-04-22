import sys
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score

df = pd.read_csv("/Users/vedantsharma/Desktop/modelml/mental_health_wearable_data.csv")

X = df.iloc[:, 0].values.reshape(-1, 1)
Y = df.iloc[:, -1].values

X_train, X_test, Y_train, Y_test = train_test_split(X, Y, test_size=0.2)

classifier = LogisticRegression()
classifier.fit(X_train, Y_train)

y_pred = classifier.predict(X_test)
print("Accuracy:", accuracy_score(Y_test, y_pred))

if len(sys.argv) > 1:
    bpm = float(sys.argv[1])
    prediction = classifier.predict([[bpm]])
    print("Predicted Health:", prediction[0])
